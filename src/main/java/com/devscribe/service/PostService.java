package com.devscribe.service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devscribe.dto.post.AutosavePostRequest;
import com.devscribe.dto.post.AutosavePostResponse;
import com.devscribe.dto.post.CreatePostRequest;
import com.devscribe.dto.post.PostDetailResponse;
import com.devscribe.dto.post.PostSummaryResponse;
import com.devscribe.dto.post.UpdatePostRequest;
import com.devscribe.entity.Post;
import com.devscribe.entity.PostStatus;
import com.devscribe.entity.Tag;
import com.devscribe.entity.User;
import com.devscribe.realtime.PostRealtimeEvent;
import com.devscribe.realtime.PostRealtimeEventType;
import com.devscribe.realtime.PostRealtimePublisher;
import com.devscribe.repository.PostRepository;
import com.devscribe.repository.UserRepository;
import com.devscribe.util.SlugUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final TagService tagService;
    private final PostRealtimePublisher postRealtimePublisher;

    @Transactional(readOnly = true)
    public Page<PostSummaryResponse> getPosts(
            int page,
            int size,
            boolean mine,
            PostStatus status,
            String tag,
            String query
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50), Sort.by(Sort.Direction.DESC, "updatedAt"));

        String normalizedTag = normalizeTag(tag);
        String normalizedQuery = normalizeSearchQuery(query);
        Page<Post> postPage;
        if (mine) {
            User user = getCurrentUser();
            if (status != null && normalizedTag != null) {
                postPage = postRepository.findDistinctByAuthor_IdAndStatusAndTags_SlugOrderByUpdatedAtDesc(
                        user.getId(),
                        status,
                        normalizedTag,
                        pageable
                );
            } else if (status != null) {
                postPage = postRepository.findByAuthor_IdAndStatusOrderByUpdatedAtDesc(user.getId(), status, pageable);
            } else if (normalizedTag != null) {
                postPage = postRepository.findDistinctByAuthor_IdAndTags_SlugOrderByUpdatedAtDesc(
                        user.getId(),
                        normalizedTag,
                        pageable
                );
            } else {
                postPage = postRepository.findByAuthor_IdOrderByUpdatedAtDesc(user.getId(), pageable);
            }
        } else if (normalizedQuery != null) {
            postPage = postRepository.searchPublishedPosts(normalizedQuery, normalizedTag, pageable);
        } else if (normalizedTag != null) {
            postPage = postRepository.findDistinctByStatusAndTags_SlugOrderByPublishedAtDesc(
                    PostStatus.PUBLISHED,
                    normalizedTag,
                    pageable
            );
        } else {
            postPage = postRepository.findByStatusOrderByPublishedAtDesc(PostStatus.PUBLISHED, pageable);
        }

        return postPage.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public PostDetailResponse getBySlug(String slug) {
        Post post = postRepository.findBySlugAndStatus(slug, PostStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));

        return toDetail(post);
    }

    @Transactional
    public PostDetailResponse create(CreatePostRequest request) {
        User currentUser = getCurrentUser();

        String slug = createUniqueSlug(request.title());
        Post post = Post.builder()
                .author(currentUser)
                .slug(slug)
                .title(request.title().trim())
                .excerpt(request.excerpt())
                .markdownContent(request.markdownContent())
                .status(PostStatus.DRAFT)
                .build();

        post.setTags(resolveTags(request.tags()));

        Post saved = postRepository.save(post);
        postRealtimePublisher.publishPostEvent(toRealtimeEvent(saved, PostRealtimeEventType.CREATED));
        return toDetail(saved);
    }

    @Transactional
    public AutosavePostResponse autosave(AutosavePostRequest request) {
        User currentUser = getCurrentUser();

        long incomingRevision = request.clientRevision();
        if (incomingRevision < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "clientRevision must be >= 0");
        }

        if (request.postId() == null) {
            String title = normalizeAutosaveTitle(request.title());

            Post post = Post.builder()
                    .author(currentUser)
                    .slug(createUniqueSlug(title))
                    .title(title)
                    .excerpt(request.excerpt())
                    .markdownContent(normalizeAutosaveMarkdown(request.markdownContent()))
                    .status(PostStatus.DRAFT)
                    .autosaveRevision(incomingRevision)
                    .build();

            post.setTags(resolveTags(request.tags()));
            Post saved = postRepository.save(post);
            postRealtimePublisher.publishPostEvent(toRealtimeEvent(saved, PostRealtimeEventType.UPDATED));
            return new AutosavePostResponse(
                    saved.getId(),
                    saved.getSlug(),
                    saved.getAutosaveRevision(),
                    true,
                    saved.getUpdatedAt()
            );
        }

        Long postId = request.postId();
        if (postId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "postId is required for updating an existing draft");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));
        ensureOwnership(post);

        Long autosaveRevision = post.getAutosaveRevision();
        long currentRevision = autosaveRevision != null ? autosaveRevision : 0L;
        if (incomingRevision <= currentRevision) {
            return new AutosavePostResponse(
                    post.getId(),
                    post.getSlug(),
                    currentRevision,
                    false,
                    post.getUpdatedAt()
            );
        }

        String nextTitle = normalizeAutosaveTitle(request.title());
        if (!post.getTitle().equals(nextTitle)) {
            post.setTitle(nextTitle);
            post.setSlug(createUniqueSlug(nextTitle, post.getId()));
        }

        post.setExcerpt(request.excerpt());
        post.setMarkdownContent(normalizeAutosaveMarkdown(request.markdownContent()));
        post.setTags(resolveTags(request.tags()));
        post.setAutosaveRevision(incomingRevision);

        Post saved = postRepository.save(post);
        postRealtimePublisher.publishPostEvent(toRealtimeEvent(saved, PostRealtimeEventType.UPDATED));
        return new AutosavePostResponse(
                saved.getId(),
                saved.getSlug(),
                saved.getAutosaveRevision(),
                true,
                saved.getUpdatedAt()
        );
    }

    @Transactional
    public PostDetailResponse update(@NonNull Long id, UpdatePostRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));
        ensureOwnership(post);

        if (!post.getTitle().equals(request.title().trim())) {
            post.setTitle(request.title().trim());
            post.setSlug(createUniqueSlug(request.title(), post.getId()));
        }
        post.setExcerpt(request.excerpt());
        post.setMarkdownContent(request.markdownContent());
        post.setTags(resolveTags(request.tags()));

        Post saved = postRepository.save(post);
        postRealtimePublisher.publishPostEvent(toRealtimeEvent(saved, PostRealtimeEventType.UPDATED));
        return toDetail(saved);
    }

    @Transactional
    public PostDetailResponse updateTags(@NonNull Long id, List<String> tags) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));
        ensureOwnership(post);

        post.setTags(resolveTags(tags));
        Post saved = postRepository.save(post);
        postRealtimePublisher.publishPostEvent(toRealtimeEvent(saved, PostRealtimeEventType.UPDATED));
        return toDetail(saved);
    }

    @Transactional
    public void delete(@NonNull Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));
        ensureOwnership(post);

        postRealtimePublisher.publishPostEvent(toRealtimeEvent(post, PostRealtimeEventType.DELETED));
        postRepository.deleteById(id);
    }

    @Transactional
    public PostDetailResponse publish(@NonNull Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));
        ensureOwnership(post);

        post.setStatus(PostStatus.PUBLISHED);
        if (post.getPublishedAt() == null) {
            post.setPublishedAt(OffsetDateTime.now());
        }

        Post saved = postRepository.save(post);
        postRealtimePublisher.publishPostEvent(toRealtimeEvent(saved, PostRealtimeEventType.PUBLISHED));
        return toDetail(saved);
    }

    private void ensureOwnership(Post post) {
        User currentUser = getCurrentUser();
        if (!post.getAuthor().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(FORBIDDEN, "You do not have access to this post");
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(FORBIDDEN, "Authentication required");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
    }

    private String createUniqueSlug(String title) {
        return createUniqueSlug(title, null);
    }

    private String createUniqueSlug(String title, Long currentPostId) {
        String baseSlug = SlugUtil.toSlug(title);
        String slug = baseSlug;
        int suffix = 1;

        while (true) {
            Post existing = postRepository.findBySlug(slug).orElse(null);
            if (existing == null || (currentPostId != null && existing.getId().equals(currentPostId))) {
                return slug;
            }

            suffix += 1;
            slug = baseSlug + "-" + suffix;
        }
    }

    private String normalizeAutosaveTitle(String title) {
        if (title == null || title.isBlank()) {
            return "Untitled draft";
        }
        return title.trim();
    }

    private String normalizeAutosaveMarkdown(String markdownContent) {
        if (markdownContent == null) {
            return "";
        }
        return markdownContent;
    }

    private PostSummaryResponse toSummary(Post post) {
        return new PostSummaryResponse(
                post.getId(),
                post.getSlug(),
                post.getTitle(),
                post.getExcerpt(),
                post.getAuthor().getUsername(),
                toTagSlugs(post),
                post.getStatus(),
                post.getPublishedAt(),
                post.getUpdatedAt()
        );
    }

    private PostDetailResponse toDetail(Post post) {
        return new PostDetailResponse(
                post.getId(),
                post.getSlug(),
                post.getTitle(),
                post.getExcerpt(),
                post.getMarkdownContent(),
                post.getAuthor().getUsername(),
                post.getStatus(),
                post.getPublishedAt(),
                post.getUpdatedAt(),
                toTagSlugs(post),
                0
        );
    }

    private Set<Tag> resolveTags(List<String> tags) {
        try {
            return tagService.resolveAndUpsert(tags);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage());
        }
    }

    private String normalizeTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        return SlugUtil.toSlug(tag);
    }

    private String normalizeSearchQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }

        return query.trim();
    }

    private List<String> toTagSlugs(Post post) {
        return post.getTags().stream()
                .map(Tag::getSlug)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private PostRealtimeEvent toRealtimeEvent(Post post, PostRealtimeEventType eventType) {
        return new PostRealtimeEvent(
                post.getId(),
                post.getSlug(),
                post.getStatus(),
                eventType,
                OffsetDateTime.now()
        );
    }
}
