package com.devscribe.repository;

import com.devscribe.entity.Comment;
import com.devscribe.entity.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Find all active top-level comments for a post (replies = null parentCommentId)
     * Ordered by creation date ascending.
     */
    @Query("""
            SELECT c FROM Comment c
            WHERE c.post.id = :postId
            AND c.parentComment IS NULL
            AND c.status = :status
            ORDER BY c.createdAt ASC
            """)
    Page<Comment> findTopLevelCommentsByPostId(
            @Param("postId") Long postId,
            @Param("status") CommentStatus status,
            Pageable pageable
    );

    /**
     * Find all active replies to a parent comment.
     */
    @Query("""
            SELECT c FROM Comment c
            WHERE c.parentComment.id = :parentCommentId
            AND c.status = :status
            ORDER BY c.createdAt ASC
            """)
    List<Comment> findRepliesByParentCommentId(
            @Param("parentCommentId") Long parentCommentId,
            @Param("status") CommentStatus status
    );

    /**
     * Count active comments on a post (including all threads).
     */
    @Query("""
            SELECT COUNT(c) FROM Comment c
            WHERE c.post.id = :postId
            AND c.status = :status
            """)
    long countActiveCommentsByPostId(
            @Param("postId") Long postId,
            @Param("status") CommentStatus status
    );

    /**
     * Find a comment by ID if it exists and is not deleted.
     */
    @Query("""
            SELECT c FROM Comment c
            WHERE c.id = :id
            AND c.status != :deletedStatus
            """)
    Optional<Comment> findByIdNotDeleted(
            @Param("id") Long id,
            @Param("deletedStatus") CommentStatus deletedStatus
    );
}

