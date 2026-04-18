package com.devscribe.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devscribe.entity.Series;

public interface SeriesRepository extends JpaRepository<Series, Long> {

    List<Series> findByAuthor_IdOrderByUpdatedAtDesc(Long authorId);

    boolean existsBySlug(String slug);
}

