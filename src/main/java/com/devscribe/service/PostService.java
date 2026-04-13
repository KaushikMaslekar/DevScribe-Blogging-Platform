package com.devscribe.service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

import com.devscribe.config.CachingConfig;
import com.devscribe.dto.post.AutosavePostRequest;
import com.devscribe.dto.post.AutosavePostResponse;
import com.devscribe.dto.post.AutosaveSnapshotResponse;
import com.devscribe.dto.post.CreatePostRequest;
import com.devscribe.dto.post.PostBookmarkResponse;
import com.devscribe.dto.post.PostDetailResponse;
import com.devscribe.dto.post.PostLikeResponse;
import com.devscribe.dto.post.PostSummaryResponse;
import com.devscribe.dto.post.RestoreAutosaveResponse;
import com.devscribe.dto.post.TrashPostResponse;
import com.devscribe.dto.post.UpdatePostRequest;
import com.devscribe.entity.Post;
import com.devscribe.entity.PostAutosaveSnapshot;
import com.devscribe.entity.PostBookmark;
import com.devscribe.entity.PostLike;
import com.devscribe.entity.PostStatus;
import com.devscribe.entity.Tag;
import com.devscribe.entity.User;
import com.devscribe.entity.UserRole;
import com.devscribe.realtime.PostRealtimeEvent;
import com.devscribe.realtime.PostRealtimeEventType;
import com.devscribe.realtime.PostRealtimePublisher;
import com.devscribe.repository.PostAutosaveSnapshotRepository;
import com.devscribe.repository.PostBookmarkRepository;
import com.devscribe.repository.PostLikeRepository;
import com.devscribe.repository.PostRepository;
import com.devscribe.repository.UserFollowRepository;
import com.devscribe.repository.UserRepository;
import com.devscribe.util.SlugUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostService {

    private static final int MAX_AUTOSAVE_SNAPSHOTS = 50;

    private final PostRepository postRepository;
    private final PostAutosaveSnapshotRepository postAutosaveSnapshotRepository;
    private final PostBookmarkRepository postBookmarkRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserFollowRepository userFollowRepository;
    private final UserRepository userRepository;
    private final TagService tagService;
    private final AuditLogService auditLogService;
    private final CacheManager cacheManager;
    private final PostRealtimePublisher postRealtimePublisher;

    @Transactional(readOnly = true)
    public Page<PostSummaryResponse> getPosts(
            int page,
            int size,
            boolean mine,
            boolean following,
            PostStatus status,
            String tag,
            String query
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50), Sort.by(Sort.Direction.DESC, "updatedAt"));

        String normalizedTag = normalizeTag(tag);
        String normalizedQuery = normalizeSearchQuery(query);
        Page<Post> postPage;
        if (following) {
            User currentUser = getCurrentUser();
            List<Long> followedAuthorIds = userFollowRepository.findFollowedIdsByFollowerId(currentUser.getId());
            if (followedAuthorIds.isEmpty()) {
                return Page.empty(pageable);
            }

            postPage = postRepository.findDistinctByAuthor_IdInAndStatusOrderByPublishedAtDesc(
                    followedAuthorIds,
                    PostStatus.PUBLISHED,
                    pageable
            );
        } else if (mine) {
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

        Map<Long, Long> likesByPostId = resolveLikesByPostId(postPage.getContent());
        Set<Long> likedPostIds = resolveLikedPostIds(postPage.getContent());
        Set<Long> bookmarkedPostIds = resolveBookmarkedPostIds(postPage.getContent());
        Set<Long> followedAuthorIds = resolveFollowedAuthorIds(postPage.getContent());

        return postPage.map(post -> toSummary(post, likesByPostId, likedPostIds, bookmarkedPostIds, followedAuthorIds));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CachingConfig.CACHE_POST_BY_SLUG, key = "#slug")
    public PostDetailResponse getBySlug(String slug) {
        Post post = postRepository.findBySlugAndStatus(slug, PostStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));

        long likesCount = postLikeRepository.countByPost_Id(post.getId());
        boolean likedByMe = isLikedByCurrentUser(post.getId());
        boolean bookmarkedByMe = isBookmarkedByCurrentUser(post.getId());
        boolean authorFollowedByMe = isAuthorFollowedByCurrentUser(post.getAuthor().getId());
        return toDetail(post, likesCount, likedByMe, bookmarkedByMe, authorFollowedByMe);
    }

    @Transactional
    @CacheEvict(cacheNames = {CachingConfig.CACHE_POST_BY_SLUG, CachingConfig.CACHE_PUBLISHED_POSTS}, allEntries = true)
    public PostLikeResponse like(@NonNull Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only published posts can be liked");
        }

        User currentUser = getCurrentUser();
        if (!postLikeRepository.existsByPost_IdAndUser_Id(postId, currentUser.getId())) {
            postLikeRepository.save(PostLike.builder().post(post).user(currentUser).build());
        }

        return new PostLikeResponse(postId, postLikeRepository.countByPost_Id(postId), true);
    }

    @Transactional
    @CacheEvict(cacheNames = {CachingConfig.CACHE_POST_BY_SLUG, CachingConfig.CACHE_PUBLISHED_POSTS}, allEntries = true)
    public PostLikeResponse unlike(@NonNull Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only published posts can be unliked");
        }

        User currentUser = getCurrentUser();
        postLikeRepository.deleteByPostIdAndUserId(postId, currentUser.getId());

        return new PostLikeResponse(postId, postLikeRepository.countByPost_Id(postId), false);
    }

    @Transactional
    @CacheEvict(cacheNames = {CachingConfig.CACHE_POST_BY_SLUG, CachingConfig.CACHE_PUBLISHED_POSTS}, allEntries = true)
    public PostBookmarkResponse bookmark(@NonNull Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only published posts can be bookmarked");
        }

        User currentUser = getCurrentUser();
        if (!postBookmarkRepository.existsByUser_IdAndPost_Id(currentUser.getId(), postId)) {
            postBookmarkRepository.save(PostBookmark.builder().user(currentUser).post(post).build());
        }

        return new PostBookmarkResponse(postId, true);
    }

    @Transactional
    @CacheEvict(cacheNames = {CachingConfig.CACHE_POST_BY_SLUG, CachingConfig.CACHE_PUBLISHED_POSTS}, allEntries = true)
    public PostBookmarkResponse unbookmark(@NonNull Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only published posts can be unbookmarked");
        }

        User currentUser = getCurrentUser();
        postBookmarkRepository.deleteByUser_IdAndPost_Id(currentUser.getId(), postId);

        return new PostBookmarkResponse(postId, false);
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryResponse> getBookmarkedPosts(int page, int size) {
        User currentUser = getCurrentUser();
        Pageable pageable = PageRequest.of(page, Math.min(size, 50), Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<PostBookmark> bookmarkPage = postBookmarkRepository.findByUser_IdOrderByCreatedAtDesc(currentUser.getId(), pageable);
        List<Post> posts = bookmarkPage.map(PostBookmark::getPost).getContent();

        Map<Long, Long> likesByPostId = resolveLikesByPostId(posts);
        Set<Long> likedPostIds = resolveLikedPostIds(posts);
        Set<Long> bookmarkedPostIds = resolveBookmarkedPostIds(posts);
        Set<Long> followedAuthorIds = resolveFollowedAuthorIds(posts);

        List<PostSummaryResponse> content = posts.stream()
                .map(post -> toSummary(post, likesByPostId, likedPostIds, bookmarkedPostIds, followedAuthorIds))
                .toList();

        return new PageImpl<>(content, pageable, bookmarkPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<TrashPostResponse> getTrash(int page, int size) {
        User currentUser = getCurrentUser();
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));

        Page<Post> deletedPosts = postRepository.findDeletedByAuthorId(currentUser.getId(), pageable);
        return deletedPosts.map(post -> new TrashPostResponse(
                post.getId(),
                post.getSlug(),
                post.getTitle(),
                post.getDeletedAt(),
                post.getDeletedBy() != null ? post.getDeletedBy().getUsername() : null,
                post.getUpdatedAt()
        ));
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
        auditLogService.log(
                currentUser,
                "POST_CREATED",
                "POST",
                String.valueOf(saved.getId()),
                "slug=" + saved.getSlug()
        );
        return toDetail(
                saved,
                postLikeRepository.countByPost_Id(saved.getId()),
                isLikedByCurrentUser(saved.getId()),
                isBookmarkedByCurrentUser(saved.getId()),
                isAuthorFollowedByCurrentUser(saved.getAuthor().getId())
        );
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
            saveAutosaveSnapshot(saved);
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
        saveAutosaveSnapshot(saved);
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
    @CacheEvict(cacheNames = {CachingConfig.CACHE_POST_BY_SLUG, CachingConfig.CACHE_PUBLISHED_POSTS}, allEntries = true)
    public PostDetailResponse update(@NonNull Long id, UpdatePostRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));
        ensureCanManagePost(post);

        if (!post.getTitle().equals(request.title().trim())) {
            post.setTitle(request.title().trim());
            post.setSlug(createUniqueSlug(request.title(), post.getId()));
        }
        post.setExcerpt(request.excerpt());
        post.setMarkdownContent(request.markdownContent());
        post.setTags(resolveTags(request.tags()));

        Post saved = postRepository.save(post);
        postRealtimePublisher.publishPostEvent(toRealtimeEvent(saved, PostRealtimeEventType.UPDATED));
        auditLogService.log(
                getCurrentUser(),
                "POST_UPDATED",
                "POST",
                String.valueOf(saved.getId()),
                "slug=" + saved.getSlug()
        );
        return toDetail(
                saved,
                postLikeRepository.countByPost_Id(saved.getId()),
                isLikedByCurrentUser(saved.getId()),
                isBookmarkedByCurrentUser(saved.getId()),
                isAuthorFollowedByCurrentUser(saved.getAuthor().getId())
        );
    }

    @Transactional
    @CacheEvict(cacheNames = {CachingConfig.CACHE_POST_BY_SLUG, CachingConfig.CACHE_PUBLISHED_POSTS}, allEntries = true)
    public PostDetailResponse updateTags(@NonNull Long id, List<String> tags) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));
        ensureCanManagePost(post);

        post.setTags(resolveTags(tags));
        Post saved = postRepository.save(post);
        postRealtimePublisher.publishPostEvent(toRealtimeEvent(saved, PostRealtimeEventType.UPDATED));
        auditLogService.log(
                getCurrentUser(),
                "POST_TAGS_UPDATED",
                "POST",
                String.valueOf(saved.getId()),
                "tagsCount=" + saved.getTags().size()
        );
        return toDetail(
                saved,
                postLikeRepository.countByPost_Id(saved.getId()),
                isLikedByCurrentUser(saved.getId()),
                isBookmarkedByCurrentUser(saved.getId()),
                isAuthorFollowedByCurrentUser(saved.getAuthor().getId())
        );
    }

    @Transactional
    @CacheEvict(cacheNames = {CachingConfig.CACHE_POST_BY_SLUG, CachingConfig.CACHE_PUBLISHED_POSTS}, allEntries = true)
    public void delete(@NonNull Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));
        User actor = getCurrentUser();
        if (!post.getAuthor().getId().equals(actor.getId()) && !hasElevatedEditorialRole(actor)) {
            throw new ResponseStatusException(FORBIDDEN, "You do not have access to this post");
        }

        post.setDeletedAt(OffsetDateTime.now());
        post.setDeletedBy(actor);
        postRepository.save(post);
        postRealtimePublisher.publishPostEvent(toRealtimeEvent(post, PostRealtimeEventType.DELETED));
        auditLogService.log(
                actor,
                "POST_SOFT_DELETED",
                "POST",
                String.valueOf(post.getId()),
                "slug=" + post.getSlug()
        );
    }

    @Transactional
    @CacheEvict(cacheNames = {CachingConfig.CACHE_POST_BY_SLUG, CachingConfig.CACHE_PUBLISHED_POSTS}, allEntries = true)
    public PostDetailResponse restore(@NonNull Long id) {
        Post post = postRepository.findDeletedById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deleted post not found"));

        User actor = getCurrentUser();
        if (!post.getAuthor().getId().equals(actor.getId()) && !hasElevatedEditorialRole(actor)) {
            throw new ResponseStatusException(FORBIDDEN, "You do not have access to this post");
        }

        post.setDeletedAt(null);
        post.setDeletedBy(null);
        Post saved = postRepository.save(post);
        postRealtimePublisher.publishPostEvent(toRealtimeEvent(saved, PostRealtimeEventType.UPDATED));
        auditLogService.log(
                actor,
                "POST_RESTORED",
                "POST",
                String.valueOf(saved.getId()),
                "slug=" + saved.getSlug()
        );

        return toDetail(
                saved,
                postLikeRepository.countByPost_Id(saved.getId()),
                isLikedByCurrentUser(saved.getId()),
                isBookmarkedByCurrentUser(saved.getId()),
                isAuthorFollowedByCurrentUser(saved.getAuthor().getId())
        );
    }

    @Transactional
    @CacheEvict(cacheNames = {CachingConfig.CACHE_POST_BY_SLUG, CachingConfig.CACHE_PUBLISHED_POSTS}, allEntries = true)
    public PostDetailResponse publish(@NonNull Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));
        ensureCanManagePost(post);

        post.setStatus(PostStatus.PUBLISHED);
        if (post.getPublishedAt() == null) {
            post.setPublishedAt(OffsetDateTime.now());
        }

        Post saved = postRepository.save(post);
        postRealtimePublisher.publishPostEvent(toRealtimeEvent(saved, PostRealtimeEventType.PUBLISHED));
        auditLogService.log(
                getCurrentUser(),
                "POST_PUBLISHED",
                "POST",
                String.valueOf(saved.getId()),
                "slug=" + saved.getSlug()
        );
        return toDetail(
                saved,
                postLikeRepository.countByPost_Id(saved.getId()),
                isLikedByCurrentUser(saved.getId()),
                isBookmarkedByCurrentUser(saved.getId()),
                isAuthorFollowedByCurrentUser(saved.getAuthor().getId())
        );
    }

    @Transactional
    public int publishDueScheduledPosts() {
        List<Post> duePosts = postRepository
                .findByStatusAndScheduledPublishAtIsNotNullAndScheduledPublishAtLessThanEqualOrderByScheduledPublishAtAsc(
                        PostStatus.DRAFT,
                        OffsetDateTime.now(),
                        PageRequest.of(0, 50)
                )
                .getContent();

        if (duePosts.isEmpty()) {
            return 0;
        }

        OffsetDateTime publishedAt = OffsetDateTime.now();
        for (Post post : duePosts) {
            post.setStatus(PostStatus.PUBLISHED);
            if (post.getPublishedAt() == null) {
                post.setPublishedAt(publishedAt);
            }
            post.setScheduledPublishAt(null);

            Post saved = postRepository.save(post);
            postRealtimePublisher.publishPostEvent(toRealtimeEvent(saved, PostRealtimeEventType.PUBLISHED));
        }

        evictPublishedPostCaches();
        return duePosts.size();
    }

    @Transactional(readOnly = true)
    public List<AutosaveSnapshotResponse> getAutosaveTimeline(@NonNull Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));
        ensureOwnership(post);

        return postAutosaveSnapshotRepository.findTop50ByPost_IdOrderByCreatedAtDesc(postId)
                .stream()
                .map(this::toAutosaveSnapshotResponse)
                .toList();
    }

    @Transactional
    public RestoreAutosaveResponse restoreAutosaveSnapshot(@NonNull Long postId, @NonNull Long snapshotId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));
        ensureOwnership(post);

        PostAutosaveSnapshot snapshot = postAutosaveSnapshotRepository.findByIdAndPost_Id(snapshotId, postId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Autosave snapshot not found"));

        post.setTitle(normalizeAutosaveTitle(snapshot.getTitle()));
        post.setSlug(createUniqueSlug(post.getTitle(), post.getId()));
        post.setExcerpt(snapshot.getExcerpt());
        post.setMarkdownContent(normalizeAutosaveMarkdown(snapshot.getMarkdownContent()));
        post.setTags(resolveTags(parseTagsCsv(snapshot.getTagsCsv())));
        post.setAutosaveRevision(Math.max(snapshot.getRevision() + 1, post.getAutosaveRevision() + 1));

        Post saved = postRepository.save(post);
        saveAutosaveSnapshot(saved);
        postRealtimePublisher.publishPostEvent(toRealtimeEvent(saved, PostRealtimeEventType.UPDATED));

        return new RestoreAutosaveResponse(
                saved.getId(),
                saved.getSlug(),
                saved.getAutosaveRevision(),
                saved.getTitle(),
                saved.getExcerpt(),
                saved.getMarkdownContent(),
                saved.getScheduledPublishAt(),
                toTagSlugs(saved),
                saved.getUpdatedAt()
        );
    }

    private void ensureOwnership(Post post) {
        User currentUser = getCurrentUser();
        if (!post.getAuthor().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(FORBIDDEN, "You do not have access to this post");
        }
    }

    private void ensureCanManagePost(Post post) {
        User currentUser = getCurrentUser();
        if (post.getAuthor().getId().equals(currentUser.getId())) {
            return;
        }

        if (hasElevatedEditorialRole(currentUser)) {
            return;
        }

        throw new ResponseStatusException(FORBIDDEN, "You do not have access to this post");
    }

    private boolean hasElevatedEditorialRole(User user) {
        return user.getRole() == UserRole.EDITOR || user.getRole() == UserRole.ADMIN;
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
            Post existing = postRepository.findAnyBySlugIncludingDeleted(slug).orElse(null);
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

    private AutosaveSnapshotResponse toAutosaveSnapshotResponse(PostAutosaveSnapshot snapshot) {
        return new AutosaveSnapshotResponse(
                snapshot.getId(),
                snapshot.getRevision(),
                snapshot.getTitle(),
                snapshot.getExcerpt(),
                snapshot.getMarkdownContent(),
                snapshot.getScheduledPublishAt(),
                parseTagsCsv(snapshot.getTagsCsv()),
                snapshot.getCreatedAt()
        );
    }

    private void saveAutosaveSnapshot(Post post) {
        PostAutosaveSnapshot snapshot = PostAutosaveSnapshot.builder()
                .post(post)
                .revision(post.getAutosaveRevision())
                .title(post.getTitle())
                .excerpt(post.getExcerpt())
                .markdownContent(post.getMarkdownContent())
                .scheduledPublishAt(post.getScheduledPublishAt())
                .tagsCsv(String.join(",", toTagSlugs(post)))
                .build();

        postAutosaveSnapshotRepository.save(snapshot);
        postAutosaveSnapshotRepository.deleteOlderSnapshots(post.getId(), MAX_AUTOSAVE_SNAPSHOTS);
    }

    private List<String> parseTagsCsv(String tagsCsv) {
        if (tagsCsv == null || tagsCsv.isBlank()) {
            return List.of();
        }

        return List.of(tagsCsv.split(","))
                .stream()
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .toList();
    }

    private PostSummaryResponse toSummary(
            Post post,
            Map<Long, Long> likesByPostId,
            Set<Long> likedPostIds,
            Set<Long> bookmarkedPostIds,
            Set<Long> followedAuthorIds
    ) {
        return new PostSummaryResponse(
                post.getId(),
                post.getSlug(),
                post.getTitle(),
                post.getExcerpt(),
                post.getAuthor().getUsername(),
                toTagSlugs(post),
                post.getStatus(),
                post.getPublishedAt(),
                post.getScheduledPublishAt(),
                post.getUpdatedAt(),
                likesByPostId.getOrDefault(post.getId(), 0L),
                likedPostIds.contains(post.getId()),
                bookmarkedPostIds.contains(post.getId()),
                followedAuthorIds.contains(post.getAuthor().getId())
        );
    }

    private PostDetailResponse toDetail(
            Post post,
            long likesCount,
            boolean likedByMe,
            boolean bookmarkedByMe,
            boolean authorFollowedByMe
    ) {
        return new PostDetailResponse(
                post.getId(),
                post.getSlug(),
                post.getTitle(),
                post.getExcerpt(),
                post.getMarkdownContent(),
                post.getAuthor().getUsername(),
                post.getStatus(),
                post.getPublishedAt(),
                post.getScheduledPublishAt(),
                post.getUpdatedAt(),
                toTagSlugs(post),
                0,
                likesCount,
                likedByMe,
                bookmarkedByMe,
                authorFollowedByMe
        );
    }

    private Map<Long, Long> resolveLikesByPostId(List<Post> posts) {
        if (posts.isEmpty()) {
            return Map.of();
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, Long> likesByPostId = new HashMap<>();
        for (PostLikeRepository.PostLikeCountProjection projection : postLikeRepository.countLikesByPostIds(postIds)) {
            likesByPostId.put(projection.getPostId(), projection.getLikeCount());
        }
        return likesByPostId;
    }

    private Set<Long> resolveLikedPostIds(List<Post> posts) {
        if (posts.isEmpty()) {
            return Set.of();
        }

        User currentUser = getCurrentUserOrNull();
        if (currentUser == null) {
            return Set.of();
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();
        return new HashSet<>(postLikeRepository.findLikedPostIdsByUserIdAndPostIds(currentUser.getId(), postIds));
    }

    private boolean isLikedByCurrentUser(Long postId) {
        User currentUser = getCurrentUserOrNull();
        return currentUser != null && postLikeRepository.existsByPost_IdAndUser_Id(postId, currentUser.getId());
    }

    private Set<Long> resolveBookmarkedPostIds(List<Post> posts) {
        if (posts.isEmpty()) {
            return Set.of();
        }

        User currentUser = getCurrentUserOrNull();
        if (currentUser == null) {
            return Set.of();
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();
        return new HashSet<>(postBookmarkRepository.findBookmarkedPostIdsByUserIdAndPostIds(currentUser.getId(), postIds));
    }

    private Set<Long> resolveFollowedAuthorIds(List<Post> posts) {
        if (posts.isEmpty()) {
            return Set.of();
        }

        User currentUser = getCurrentUserOrNull();
        if (currentUser == null) {
            return Set.of();
        }

        Set<Long> authorIds = posts.stream().map(post -> post.getAuthor().getId()).collect(HashSet::new, Set::add, Set::addAll);
        List<Long> followedAuthorIds = userFollowRepository.findFollowedIdsByFollowerId(currentUser.getId());
        return followedAuthorIds.stream().filter(authorIds::contains).collect(HashSet::new, Set::add, Set::addAll);
    }

    private boolean isBookmarkedByCurrentUser(Long postId) {
        User currentUser = getCurrentUserOrNull();
        return currentUser != null && postBookmarkRepository.existsByUser_IdAndPost_Id(currentUser.getId(), postId);
    }

    private boolean isAuthorFollowedByCurrentUser(Long authorId) {
        User currentUser = getCurrentUserOrNull();
        return currentUser != null && userFollowRepository.existsByFollower_IdAndFollowed_Id(currentUser.getId(), authorId);
    }

    private User getCurrentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return null;
        }

        return userRepository.findByEmail(authentication.getName()).orElse(null);
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

    private void evictPublishedPostCaches() {
        Cache postBySlugCache = cacheManager.getCache(CachingConfig.CACHE_POST_BY_SLUG);
        if (postBySlugCache != null) {
            postBySlugCache.clear();
        }

        Cache publishedPostsCache = cacheManager.getCache(CachingConfig.CACHE_PUBLISHED_POSTS);
        if (publishedPostsCache != null) {
            publishedPostsCache.clear();
        }
    }
}
