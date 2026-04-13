package com.devscribe.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devscribe.entity.PostSeries;

public interface PostSeriesRepository extends JpaRepository<PostSeries, Long> {

    Optional<PostSeries> findByAuthor_IdAndSlug(Long authorId, String slug);
}
