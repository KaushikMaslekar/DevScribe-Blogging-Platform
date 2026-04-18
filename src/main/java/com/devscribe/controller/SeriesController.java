package com.devscribe.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devscribe.dto.series.AttachSeriesPostRequest;
import com.devscribe.dto.series.CreateSeriesRequest;
import com.devscribe.dto.series.MoveSeriesPostRequest;
import com.devscribe.dto.series.ReorderSeriesPostsRequest;
import com.devscribe.dto.series.SeriesPostsResponse;
import com.devscribe.dto.series.SeriesSummaryResponse;
import com.devscribe.service.SeriesService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/series")
@RequiredArgsConstructor
public class SeriesController {

    private final SeriesService seriesService;

    @PostMapping
    public ResponseEntity<SeriesSummaryResponse> create(@Valid @RequestBody CreateSeriesRequest request) {
        return ResponseEntity.ok(seriesService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<SeriesSummaryResponse>> listMine() {
        return ResponseEntity.ok(seriesService.listMine());
    }

    @GetMapping("/{seriesId}/posts")
    public ResponseEntity<SeriesPostsResponse> listPosts(@PathVariable @NonNull Long seriesId) {
        return ResponseEntity.ok(seriesService.listPosts(seriesId));
    }

    @PostMapping("/{seriesId}/posts")
    public ResponseEntity<SeriesPostsResponse> attachPost(
            @PathVariable @NonNull Long seriesId,
            @Valid @RequestBody AttachSeriesPostRequest request
    ) {
        return ResponseEntity.ok(seriesService.attachPost(seriesId, request));
    }

    @DeleteMapping("/{seriesId}/posts/{postId}")
    public ResponseEntity<SeriesPostsResponse> detachPost(
            @PathVariable @NonNull Long seriesId,
            @PathVariable @NonNull Long postId
    ) {
        return ResponseEntity.ok(seriesService.detachPost(seriesId, postId));
    }

    @PatchMapping("/{seriesId}/posts/{postId}/move")
    public ResponseEntity<SeriesPostsResponse> movePost(
            @PathVariable @NonNull Long seriesId,
            @PathVariable @NonNull Long postId,
            @Valid @RequestBody MoveSeriesPostRequest request
    ) {
        return ResponseEntity.ok(seriesService.movePost(seriesId, postId, request));
    }

    @PutMapping("/{seriesId}/posts/reorder")
    public ResponseEntity<SeriesPostsResponse> reorderPosts(
            @PathVariable @NonNull Long seriesId,
            @Valid @RequestBody ReorderSeriesPostsRequest request
    ) {
        return ResponseEntity.ok(seriesService.reorderPosts(seriesId, request));
    }
}
