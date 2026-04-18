package com.devscribe.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devscribe.dto.series.AttachSeriesPostRequest;
import com.devscribe.dto.series.CreateSeriesRequest;
import com.devscribe.dto.series.MoveSeriesPostRequest;
import com.devscribe.dto.series.ReorderSeriesPostsRequest;
import com.devscribe.dto.series.SeriesPostItemResponse;
import com.devscribe.dto.series.SeriesPostsResponse;
import com.devscribe.dto.series.SeriesSummaryResponse;
import com.devscribe.entity.Post;
import com.devscribe.entity.Series;
import com.devscribe.entity.SeriesPost;
import com.devscribe.entity.User;
import com.devscribe.repository.PostRepository;
import com.devscribe.repository.SeriesPostRepository;
import com.devscribe.repository.SeriesRepository;
import com.devscribe.repository.UserRepository;
import com.devscribe.util.SlugUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SeriesService {

    private final SeriesRepository seriesRepository;
    private final SeriesPostRepository seriesPostRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public SeriesSummaryResponse create(CreateSeriesRequest request) {
        User currentUser = getCurrentUser();
        String normalizedTitle = request.title().trim();
        String slug = createUniqueSlug(normalizedTitle);

        Series series = Series.builder()
                .author(currentUser)
                .slug(slug)
                .title(normalizedTitle)
                .description(normalizeDescription(request.description()))
                .build();

        Series saved = seriesRepository.save(series);
        return toSummary(saved);
    }

    @Transactional(readOnly = true)
    public List<SeriesSummaryResponse> listMine() {
        User currentUser = getCurrentUser();
        return seriesRepository.findByAuthor_IdOrderByUpdatedAtDesc(currentUser.getId()).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public SeriesPostsResponse listPosts(@NonNull Long seriesId) {
        Series series = getOwnedSeries(seriesId);
        return toSeriesPostsResponse(series.getId());
    }

    @Transactional
    public SeriesPostsResponse attachPost(@NonNull Long seriesId, AttachSeriesPostRequest request) {
        Series series = getOwnedSeries(seriesId);
        Post post = getOwnedPost(request.postId());

        if (seriesPostRepository.findBySeries_IdAndPost_Id(seriesId, post.getId()).isPresent()) {
            throw new ResponseStatusException(BAD_REQUEST, "Post is already attached to this series");
        }

        SeriesPost existingSeriesPost = seriesPostRepository.findByPost_Id(post.getId()).orElse(null);
        if (existingSeriesPost != null && !existingSeriesPost.getSeries().getId().equals(seriesId)) {
            throw new ResponseStatusException(BAD_REQUEST, "Post is already attached to another series");
        }

        List<SeriesPost> currentSeriesPosts = seriesPostRepository.findBySeries_IdOrderBySortOrderAsc(seriesId);
        int targetSortOrder = resolveTargetSortOrder(request.sortOrder(), currentSeriesPosts.size());

        for (SeriesPost seriesPost : currentSeriesPosts) {
            if (seriesPost.getSortOrder() >= targetSortOrder) {
                seriesPost.setSortOrder(seriesPost.getSortOrder() + 1);
            }
        }
        if (!currentSeriesPosts.isEmpty()) {
            seriesPostRepository.saveAll(currentSeriesPosts);
        }

        seriesPostRepository.save(SeriesPost.builder()
                .series(series)
                .post(post)
                .sortOrder(targetSortOrder)
                .build());

        return toSeriesPostsResponse(seriesId);
    }

    @Transactional
    public SeriesPostsResponse detachPost(@NonNull Long seriesId, @NonNull Long postId) {
        getOwnedSeries(seriesId);

        SeriesPost seriesPost = seriesPostRepository.findBySeries_IdAndPost_Id(seriesId, postId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Series post not found"));
        int removedSortOrder = seriesPost.getSortOrder();

        seriesPostRepository.delete(seriesPost);
        compactSortOrderAfterRemoval(seriesId, removedSortOrder);

        return toSeriesPostsResponse(seriesId);
    }

    @Transactional
    public SeriesPostsResponse movePost(@NonNull Long seriesId, @NonNull Long postId, MoveSeriesPostRequest request) {
        getOwnedSeries(seriesId);

        SeriesPost seriesPost = seriesPostRepository.findBySeries_IdAndPost_Id(seriesId, postId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Series post not found"));

        Long targetSeriesId = request.targetSeriesId() == null ? seriesId : request.targetSeriesId();
        if (targetSeriesId.equals(seriesId)) {
            moveWithinSeries(seriesId, seriesPost, request.sortOrder());
            return toSeriesPostsResponse(seriesId);
        }

        Series targetSeries = getOwnedSeries(targetSeriesId);
        moveAcrossSeries(seriesId, targetSeries, seriesPost, request.sortOrder());
        return toSeriesPostsResponse(targetSeriesId);
    }

    @Transactional
    public SeriesPostsResponse reorderPosts(@NonNull Long seriesId, ReorderSeriesPostsRequest request) {
        getOwnedSeries(seriesId);
        List<SeriesPost> currentSeriesPosts = seriesPostRepository.findBySeries_IdOrderBySortOrderAsc(seriesId);

        if (currentSeriesPosts.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Series has no posts to reorder");
        }

        List<Long> requestedOrder = request.postIds();
        if (requestedOrder.size() != currentSeriesPosts.size()) {
            throw new ResponseStatusException(BAD_REQUEST, "Reorder payload must include all series posts exactly once");
        }

        Set<Long> uniqueRequestedPostIds = new HashSet<>(requestedOrder);
        if (uniqueRequestedPostIds.size() != requestedOrder.size()) {
            throw new ResponseStatusException(BAD_REQUEST, "Reorder payload contains duplicate post ids");
        }

        Set<Long> currentPostIds = currentSeriesPosts.stream()
                .map(currentSeriesPost -> currentSeriesPost.getPost().getId())
                .collect(Collectors.toSet());
        if (!currentPostIds.equals(uniqueRequestedPostIds)) {
            throw new ResponseStatusException(BAD_REQUEST, "Reorder payload must match current series posts");
        }

        Map<Long, SeriesPost> byPostId = currentSeriesPosts.stream()
                .collect(Collectors.toMap(currentSeriesPost -> currentSeriesPost.getPost().getId(), Function.identity()));

        for (int i = 0; i < requestedOrder.size(); i += 1) {
            Long requestedPostId = requestedOrder.get(i);
            SeriesPost currentSeriesPost = byPostId.get(requestedPostId);
            currentSeriesPost.setSortOrder(i + 1);
        }
        seriesPostRepository.saveAll(currentSeriesPosts);

        return toSeriesPostsResponse(seriesId);
    }

    private void moveWithinSeries(Long seriesId, SeriesPost seriesPost, Integer requestedSortOrder) {
        List<SeriesPost> currentSeriesPosts = seriesPostRepository.findBySeries_IdOrderBySortOrderAsc(seriesId);
        int currentSortOrder = seriesPost.getSortOrder();
        int targetSortOrder = resolveTargetSortOrder(requestedSortOrder, currentSeriesPosts.size());

        if (targetSortOrder == currentSortOrder) {
            return;
        }

        for (SeriesPost currentSeriesPost : currentSeriesPosts) {
            if (currentSeriesPost.getPost().getId().equals(seriesPost.getPost().getId())) {
                continue;
            }

            int sortOrder = currentSeriesPost.getSortOrder();
            if (targetSortOrder < currentSortOrder && sortOrder >= targetSortOrder && sortOrder < currentSortOrder) {
                currentSeriesPost.setSortOrder(sortOrder + 1);
            }
            if (targetSortOrder > currentSortOrder && sortOrder <= targetSortOrder && sortOrder > currentSortOrder) {
                currentSeriesPost.setSortOrder(sortOrder - 1);
            }
        }

        seriesPost.setSortOrder(targetSortOrder);
        seriesPostRepository.saveAll(currentSeriesPosts);
    }

    private void moveAcrossSeries(Long sourceSeriesId, Series targetSeries, SeriesPost seriesPost, Integer requestedSortOrder) {
        Long targetSeriesId = targetSeries.getId();
        if (seriesPostRepository.findBySeries_IdAndPost_Id(targetSeriesId, seriesPost.getPost().getId()).isPresent()) {
            throw new ResponseStatusException(BAD_REQUEST, "Post is already attached to target series");
        }

        int removedSortOrder = seriesPost.getSortOrder();
        compactSortOrderAfterRemoval(sourceSeriesId, removedSortOrder);

        List<SeriesPost> targetPosts = seriesPostRepository.findBySeries_IdOrderBySortOrderAsc(targetSeriesId);
        int targetSortOrder = resolveTargetSortOrder(requestedSortOrder, targetPosts.size());
        for (SeriesPost targetPost : targetPosts) {
            if (targetPost.getSortOrder() >= targetSortOrder) {
                targetPost.setSortOrder(targetPost.getSortOrder() + 1);
            }
        }

        seriesPost.setSeries(targetSeries);
        seriesPost.setSortOrder(targetSortOrder);
        seriesPostRepository.save(seriesPost);

        if (!targetPosts.isEmpty()) {
            seriesPostRepository.saveAll(targetPosts);
        }
    }

    private void compactSortOrderAfterRemoval(Long seriesId, int removedSortOrder) {
        List<SeriesPost> seriesPosts = seriesPostRepository.findBySeries_IdOrderBySortOrderAsc(seriesId);
        for (SeriesPost currentSeriesPost : seriesPosts) {
            if (currentSeriesPost.getSortOrder() > removedSortOrder) {
                currentSeriesPost.setSortOrder(currentSeriesPost.getSortOrder() - 1);
            }
        }

        if (!seriesPosts.isEmpty()) {
            seriesPostRepository.saveAll(seriesPosts);
        }
    }

    private int resolveTargetSortOrder(Integer requestedSortOrder, int currentSize) {
        if (requestedSortOrder == null) {
            return currentSize + 1;
        }

        if (requestedSortOrder < 1 || requestedSortOrder > currentSize + 1) {
            throw new ResponseStatusException(BAD_REQUEST, "sortOrder must be between 1 and current series size + 1");
        }

        return requestedSortOrder;
    }

    private Series getOwnedSeries(Long seriesId) {
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Series not found"));

        User currentUser = getCurrentUser();
        if (!series.getAuthor().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(FORBIDDEN, "You do not have access to this series");
        }

        return series;
    }

    private Post getOwnedPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));

        User currentUser = getCurrentUser();
        if (!post.getAuthor().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(FORBIDDEN, "You do not have access to this post");
        }

        return post;
    }

    private SeriesPostsResponse toSeriesPostsResponse(Long seriesId) {
        List<SeriesPostItemResponse> items = seriesPostRepository.findBySeries_IdOrderBySortOrderAsc(seriesId).stream()
                .map(seriesPost -> new SeriesPostItemResponse(
                        seriesPost.getPost().getId(),
                        seriesPost.getPost().getSlug(),
                        seriesPost.getPost().getTitle(),
                        seriesPost.getPost().getStatus(),
                        seriesPost.getSortOrder()
                ))
                .toList();

        return new SeriesPostsResponse(seriesId, items);
    }

    private SeriesSummaryResponse toSummary(Series series) {
        long postsCount = seriesPostRepository.countBySeries_Id(series.getId());
        return new SeriesSummaryResponse(
                series.getId(),
                series.getSlug(),
                series.getTitle(),
                series.getDescription(),
                series.getAuthor().getUsername(),
                postsCount,
                series.getCreatedAt(),
                series.getUpdatedAt()
        );
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
        String baseSlug = SlugUtil.toSlug(title);
        if (baseSlug.isBlank()) {
            baseSlug = "series";
        }

        String slug = baseSlug;
        int suffix = 1;
        while (seriesRepository.existsBySlug(slug)) {
            suffix += 1;
            slug = baseSlug + "-" + suffix;
        }

        return slug;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }

        String trimmed = description.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed;
    }
}
