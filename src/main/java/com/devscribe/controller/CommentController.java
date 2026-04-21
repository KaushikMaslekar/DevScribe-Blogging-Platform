package com.devscribe.controller;

import com.devscribe.dto.comment.CommentResponse;
import com.devscribe.dto.comment.CommentThreadResponse;
import com.devscribe.dto.comment.CreateCommentRequest;
import com.devscribe.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * GET /posts/{postId}/comments
     * Get paginated top-level comments with nested replies.
     */
    @GetMapping
    public ResponseEntity<Page<CommentThreadResponse>> getComments(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CommentThreadResponse> comments = commentService.getCommentsForPost(postId, pageable);
        return ResponseEntity.ok(comments);
    }

    /**
     * GET /posts/{postId}/comments/count
     * Get total active comment count for a post.
     */
    @GetMapping("/count")
    public ResponseEntity<CommentCountResponse> getCommentCount(@PathVariable Long postId) {
        long count = commentService.getCommentCountForPost(postId);
        return ResponseEntity.ok(new CommentCountResponse(postId, count));
    }

    /**
     * POST /posts/{postId}/comments
     * Create a new comment or reply.
     * Request body: { "content": "...", "parentCommentId": null }
     */
    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        CommentResponse response = commentService.createComment(postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * DELETE /posts/{postId}/comments/{commentId}
     * Delete a comment (only author or post owner).
     */
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId
    ) {
        commentService.deleteComment(postId, commentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /posts/{postId}/comments/{commentId}/flag
     * Flag a comment for moderation (spam/abuse).
     */
    @PostMapping("/{commentId}/flag")
    public ResponseEntity<Void> flagComment(
            @PathVariable Long postId,
            @PathVariable Long commentId
    ) {
        commentService.flagComment(postId, commentId);
        return ResponseEntity.ok().build();
    }

    // Response DTOs

    record CommentCountResponse(
            Long postId,
            long count
    ) {
    }
}

