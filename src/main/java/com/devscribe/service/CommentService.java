package com.devscribe.service;

import com.devscribe.dto.comment.AuthorInfo;
import com.devscribe.dto.comment.CommentResponse;
import com.devscribe.dto.comment.CommentThreadResponse;
import com.devscribe.dto.comment.CreateCommentRequest;
import com.devscribe.entity.Comment;
import com.devscribe.entity.CommentStatus;
import com.devscribe.entity.Post;
import com.devscribe.entity.User;
import com.devscribe.repository.CommentRepository;
import com.devscribe.repository.PostRepository;
import com.devscribe.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    /**
     * Create a new comment or reply on a post.
     */
    public CommentResponse createComment(Long postId, CreateCommentRequest request) {
        // Verify post exists
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));

        // Get current user
        User author = getCurrentUser();

        Comment comment = Comment.builder()
                .post(post)
                .author(author)
                .content(request.content())
                .status(CommentStatus.ACTIVE)
                .build();

        // If replying to another comment, set parent
        if (request.parentCommentId() != null) {
            Comment parentComment = commentRepository.findById(request.parentCommentId())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Parent comment not found"));

            // Ensure parent is on same post and not deleted
            if (!parentComment.getPost().getId().equals(postId) || 
                parentComment.getStatus() == CommentStatus.DELETED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid parent comment");
            }

            comment.setParentComment(parentComment);
        }

        Comment saved = commentRepository.save(comment);
        log.info("Comment created: id={}, postId={}, authorId={}", saved.getId(), postId, author.getId());

        return mapToCommentResponse(saved, author.getId());
    }

    /**
     * Get paginated top-level comments for a post (with replies nested).
     */
    public Page<CommentThreadResponse> getCommentsForPost(Long postId, Pageable pageable) {
        // Verify post exists
        postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Post not found"));

        User currentUser = getCurrentUserOrNull();
        Long currentUserId = currentUser != null ? currentUser.getId() : null;

        // Get top-level comments
        Page<Comment> topLevelComments = commentRepository.findTopLevelCommentsByPostId(
                postId,
                CommentStatus.ACTIVE,
                pageable
        );

        // Build nested thread responses
        List<CommentThreadResponse> threads = topLevelComments.getContent().stream()
                .map(comment -> buildCommentThread(comment, currentUserId))
                .toList();

        return new PageImpl<>(threads, pageable, topLevelComments.getTotalElements());
    }

    /**
     * Delete a comment (soft delete via status flag).
     * Only the author or post owner can delete.
     */
    public void deleteComment(Long postId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Comment not found"));

        if (!comment.getPost().getId().equals(postId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment does not belong to this post");
        }

        User currentUser = getCurrentUser();

        // Only author or post owner can delete
        boolean isAuthor = comment.getAuthor().getId().equals(currentUser.getId());
        boolean isPostOwner = comment.getPost().getAuthor().getId().equals(currentUser.getId());

        if (!isAuthor && !isPostOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to delete this comment");
        }

        comment.setStatus(CommentStatus.DELETED);
        commentRepository.save(comment);
        log.info("Comment deleted: id={}, postId={}, deletedBy={}", commentId, postId, currentUser.getId());
    }

    /**
     * Flag a comment for moderation (spam/abuse).
     */
    public void flagComment(Long postId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Comment not found"));

        if (!comment.getPost().getId().equals(postId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment does not belong to this post");
        }

        if (comment.getStatus() == CommentStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot flag a deleted comment");
        }

        comment.setStatus(CommentStatus.FLAGGED);
        commentRepository.save(comment);
        log.info("Comment flagged: id={}, postId={}", commentId, postId);
    }

    /**
     * Get total active comment count for a post (including all threads).
     */
    public long getCommentCountForPost(Long postId) {
        return commentRepository.countActiveCommentsByPostId(postId, CommentStatus.ACTIVE);
    }

    // === Private helper methods ===

    private CommentThreadResponse buildCommentThread(Comment comment, Long currentUserId) {
        CommentResponse response = mapToCommentResponse(comment, currentUserId);

        // Fetch all replies recursively
        List<Comment> replies = commentRepository.findRepliesByParentCommentId(
                comment.getId(),
                CommentStatus.ACTIVE
        );

        List<CommentThreadResponse> nestedReplies = replies.stream()
                .map(reply -> buildCommentThread(reply, currentUserId))
                .toList();

        return new CommentThreadResponse(response, nestedReplies);
    }

    private CommentResponse mapToCommentResponse(Comment comment, Long currentUserId) {
        AuthorInfo authorInfo = new AuthorInfo(
                comment.getAuthor().getId(),
                comment.getAuthor().getUsername(),
                comment.getAuthor().getDisplayName(),
                comment.getAuthor().getAvatarUrl()
        );

        boolean isAuthor = currentUserId != null && currentUserId.equals(comment.getAuthor().getId());

        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getParentComment() != null ? comment.getParentComment().getId() : null,
                authorInfo,
                comment.getContent(),
                comment.getStatus().toString(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                isAuthor
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

    private User getCurrentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email).orElse(null);
    }
}

