package com.devscribe.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devscribe.dto.post.AutosavePostRequest;
import com.devscribe.dto.post.AutosavePostResponse;
import com.devscribe.dto.post.CreatePostRequest;
import com.devscribe.dto.post.PostDetailResponse;
import com.devscribe.dto.post.PostSummaryResponse;
import com.devscribe.dto.post.UpdatePostRequest;
import com.devscribe.dto.post.UpdatePostTagsRequest;
import com.devscribe.entity.PostStatus;
import com.devscribe.service.PostService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<Page<PostSummaryResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(required = false) PostStatus status,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String query
    ) {
        return ResponseEntity.ok(postService.getPosts(page, size, mine, status, tag, query));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<PostDetailResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(postService.getBySlug(slug));
    }

    @PostMapping
    public ResponseEntity<PostDetailResponse> create(@Valid @RequestBody CreatePostRequest request) {
        return ResponseEntity.ok(postService.create(request));
    }

    @PostMapping("/autosave")
    public ResponseEntity<AutosavePostResponse> autosave(@Valid @RequestBody AutosavePostRequest request) {
        return ResponseEntity.ok(postService.autosave(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostDetailResponse> update(
            @PathVariable @NonNull Long id,
            @Valid @RequestBody UpdatePostRequest request
    ) {
        return ResponseEntity.ok(postService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<PostDetailResponse> publish(@PathVariable @NonNull Long id) {
        return ResponseEntity.ok(postService.publish(id));
    }

    @PutMapping("/{id}/tags")
    public ResponseEntity<PostDetailResponse> updateTags(
            @PathVariable @NonNull Long id,
            @Valid @RequestBody UpdatePostTagsRequest request
    ) {
        return ResponseEntity.ok(postService.updateTags(id, request.tags()));
    }
}
