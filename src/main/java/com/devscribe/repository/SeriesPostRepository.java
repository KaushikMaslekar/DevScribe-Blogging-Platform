package com.devscribe.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devscribe.entity.SeriesPost;

public interface SeriesPostRepository extends JpaRepository<SeriesPost, Long> {

    List<SeriesPost> findBySeries_IdOrderBySortOrderAsc(Long seriesId);

    Optional<SeriesPost> findBySeries_IdAndPost_Id(Long seriesId, Long postId);

    Optional<SeriesPost> findByPost_Id(Long postId);

    long countBySeries_Id(Long seriesId);

    @Query("select coalesce(max(sp.sortOrder), 0) from SeriesPost sp where sp.series.id = :seriesId")
    int findMaxSortOrderBySeriesId(@Param("seriesId") Long seriesId);
}
