package com.devscribe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.devscribe.dto.series.AttachSeriesPostRequest;
import com.devscribe.dto.series.MoveSeriesPostRequest;
import com.devscribe.dto.series.ReorderSeriesPostsRequest;
import com.devscribe.dto.series.SeriesPostsResponse;
import com.devscribe.dto.series.SeriesSummaryResponse;
import com.devscribe.entity.Post;
import com.devscribe.entity.PostStatus;
import com.devscribe.entity.Series;
import com.devscribe.entity.SeriesPost;
import com.devscribe.entity.User;
import com.devscribe.repository.PostRepository;
import com.devscribe.repository.SeriesPostRepository;
import com.devscribe.repository.SeriesRepository;
import com.devscribe.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class SeriesServicePostOrderingTest {

    @Mock
    private SeriesRepository seriesRepository;

    @Mock
    private SeriesPostRepository seriesPostRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SeriesService seriesService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void attachPostInsertsAtRequestedPosition() {
        User owner = User.builder().id(1L).email("owner@devscribe.com").username("owner").passwordHash("x").build();
        Series series = Series.builder().id(11L).author(owner).slug("spring-series").title("Spring").build();

        Post firstPost = Post.builder().id(101L).author(owner).slug("one").title("One").status(PostStatus.DRAFT).build();
        Post thirdPost = Post.builder().id(103L).author(owner).slug("three").title("Three").status(PostStatus.DRAFT).build();
        Post insertedPost = Post.builder().id(102L).author(owner).slug("two").title("Two").status(PostStatus.DRAFT).build();

        SeriesPost itemOne = SeriesPost.builder().series(series).post(firstPost).sortOrder(1).build();
        SeriesPost itemThree = SeriesPost.builder().series(series).post(thirdPost).sortOrder(2).build();

        SeriesPost insertedItem = SeriesPost.builder().series(series).post(insertedPost).sortOrder(2).build();
        List<SeriesPost> reorderedView = List.of(
                SeriesPost.builder().series(series).post(firstPost).sortOrder(1).build(),
                insertedItem,
                SeriesPost.builder().series(series).post(thirdPost).sortOrder(3).build()
        );

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("owner@devscribe.com", "n/a"));

        when(userRepository.findByEmail("owner@devscribe.com")).thenReturn(Optional.of(owner));
        when(seriesRepository.findById(11L)).thenReturn(Optional.of(series));
        when(postRepository.findById(102L)).thenReturn(Optional.of(insertedPost));
        when(seriesPostRepository.findBySeries_IdAndPost_Id(11L, 102L)).thenReturn(Optional.empty());
        when(seriesPostRepository.findByPost_Id(102L)).thenReturn(Optional.empty());
        when(seriesPostRepository.findBySeries_IdOrderBySortOrderAsc(11L))
                .thenReturn(List.of(itemOne, itemThree), reorderedView);
        when(seriesPostRepository.save(any(SeriesPost.class))).thenReturn(insertedItem);

        SeriesPostsResponse response = seriesService.attachPost(11L, new AttachSeriesPostRequest(102L, 2));

        assertEquals(3, response.posts().size());
        assertEquals(102L, response.posts().get(1).postId());
        assertEquals(3, response.posts().get(2).sortOrder());
    }

    @Test
    void detachPostCompactsOrdering() {
        User owner = User.builder().id(1L).email("owner@devscribe.com").username("owner").passwordHash("x").build();
        Series series = Series.builder().id(11L).author(owner).slug("spring-series").title("Spring").build();

        Post firstPost = Post.builder().id(101L).author(owner).slug("one").title("One").status(PostStatus.DRAFT).build();
        Post secondPost = Post.builder().id(102L).author(owner).slug("two").title("Two").status(PostStatus.DRAFT).build();
        Post thirdPost = Post.builder().id(103L).author(owner).slug("three").title("Three").status(PostStatus.DRAFT).build();

        SeriesPost first = SeriesPost.builder().series(series).post(firstPost).sortOrder(1).build();
        SeriesPost second = SeriesPost.builder().series(series).post(secondPost).sortOrder(2).build();
        SeriesPost third = SeriesPost.builder().series(series).post(thirdPost).sortOrder(3).build();

        List<SeriesPost> afterDetach = List.of(
                SeriesPost.builder().series(series).post(firstPost).sortOrder(1).build(),
                SeriesPost.builder().series(series).post(thirdPost).sortOrder(2).build()
        );

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("owner@devscribe.com", "n/a"));

        when(userRepository.findByEmail("owner@devscribe.com")).thenReturn(Optional.of(owner));
        when(seriesRepository.findById(11L)).thenReturn(Optional.of(series));
        when(seriesPostRepository.findBySeries_IdAndPost_Id(11L, 102L)).thenReturn(Optional.of(second));
        when(seriesPostRepository.findBySeries_IdOrderBySortOrderAsc(11L)).thenReturn(List.of(first, third), afterDetach);

        SeriesPostsResponse response = seriesService.detachPost(11L, 102L);

        assertEquals(2, response.posts().size());
        assertEquals(103L, response.posts().get(1).postId());
        assertEquals(2, response.posts().get(1).sortOrder());
    }

    @Test
    void movePostAcrossSeriesReturnsTargetSeriesOrdering() {
        User owner = User.builder().id(1L).email("owner@devscribe.com").username("owner").passwordHash("x").build();
        Series sourceSeries = Series.builder().id(11L).author(owner).slug("source").title("Source").build();
        Series targetSeries = Series.builder().id(12L).author(owner).slug("target").title("Target").build();

        Post movedPost = Post.builder().id(101L).author(owner).slug("one").title("One").status(PostStatus.DRAFT).build();
        Post targetPost = Post.builder().id(202L).author(owner).slug("two").title("Two").status(PostStatus.DRAFT).build();

        SeriesPost sourceItem = SeriesPost.builder().series(sourceSeries).post(movedPost).sortOrder(1).build();
        SeriesPost targetItem = SeriesPost.builder().series(targetSeries).post(targetPost).sortOrder(1).build();
        List<SeriesPost> targetAfterMove = List.of(
                SeriesPost.builder().series(targetSeries).post(movedPost).sortOrder(1).build(),
                SeriesPost.builder().series(targetSeries).post(targetPost).sortOrder(2).build()
        );

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("owner@devscribe.com", "n/a"));

        when(userRepository.findByEmail("owner@devscribe.com")).thenReturn(Optional.of(owner));
        when(seriesRepository.findById(11L)).thenReturn(Optional.of(sourceSeries));
        when(seriesRepository.findById(12L)).thenReturn(Optional.of(targetSeries));
        when(seriesPostRepository.findBySeries_IdAndPost_Id(11L, 101L)).thenReturn(Optional.of(sourceItem));
        when(seriesPostRepository.findBySeries_IdAndPost_Id(12L, 101L)).thenReturn(Optional.empty());
        when(seriesPostRepository.findBySeries_IdOrderBySortOrderAsc(11L)).thenReturn(List.of(sourceItem), List.of());
        when(seriesPostRepository.findBySeries_IdOrderBySortOrderAsc(12L)).thenReturn(List.of(targetItem), targetAfterMove);

        SeriesPostsResponse response = seriesService.movePost(11L, 101L, new MoveSeriesPostRequest(12L, 1));

        assertEquals(12L, response.seriesId());
        assertEquals(2, response.posts().size());
        assertEquals(101L, response.posts().get(0).postId());
    }

    @Test
    void listMineIncludesPostCounts() {
        User owner = User.builder().id(1L).email("owner@devscribe.com").username("owner").passwordHash("x").build();
        Series series = Series.builder().id(11L).author(owner).slug("spring-series").title("Spring").build();

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("owner@devscribe.com", "n/a"));

        when(userRepository.findByEmail("owner@devscribe.com")).thenReturn(Optional.of(owner));
        when(seriesRepository.findByAuthor_IdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(series));
        when(seriesPostRepository.countBySeries_Id(11L)).thenReturn(2L);

        List<SeriesSummaryResponse> response = seriesService.listMine();

        assertEquals(1, response.size());
        assertEquals(2L, response.get(0).postsCount());
    }

    @Test
    void reorderRejectsWhenPayloadDoesNotMatchCurrentPosts() {
        User owner = User.builder().id(1L).email("owner@devscribe.com").username("owner").passwordHash("x").build();
        Series series = Series.builder().id(11L).author(owner).slug("spring-series").title("Spring").build();

        Post firstPost = Post.builder().id(101L).author(owner).slug("one").title("One").status(PostStatus.DRAFT).build();
        Post secondPost = Post.builder().id(102L).author(owner).slug("two").title("Two").status(PostStatus.DRAFT).build();

        List<SeriesPost> currentItems = List.of(
                SeriesPost.builder().series(series).post(firstPost).sortOrder(1).build(),
                SeriesPost.builder().series(series).post(secondPost).sortOrder(2).build()
        );

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("owner@devscribe.com", "n/a"));

        when(userRepository.findByEmail("owner@devscribe.com")).thenReturn(Optional.of(owner));
        when(seriesRepository.findById(11L)).thenReturn(Optional.of(series));
        when(seriesPostRepository.findBySeries_IdOrderBySortOrderAsc(11L)).thenReturn(currentItems);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> seriesService.reorderPosts(11L, new ReorderSeriesPostsRequest(List.of(101L, 999L)))
        );

        assertEquals(400, exception.getStatusCode().value());
    }
}
