package com.devscribe.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devscribe.entity.Post;
import com.devscribe.entity.PostStatus;

public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findBySlugAndStatus(String slug, PostStatus status);

    Optional<Post> findBySlug(String slug);

    Page<Post> findByStatusOrderByPublishedAtDesc(PostStatus status, Pageable pageable);

    Page<Post> findDistinctByStatusAndTags_SlugOrderByPublishedAtDesc(PostStatus status, String tagSlug, Pageable pageable);

    Page<Post> findByAuthor_IdOrderByUpdatedAtDesc(Long authorId, Pageable pageable);

    Page<Post> findByAuthor_IdAndStatusOrderByUpdatedAtDesc(Long authorId, PostStatus status, Pageable pageable);

    Page<Post> findDistinctByAuthor_IdAndTags_SlugOrderByUpdatedAtDesc(Long authorId, String tagSlug, Pageable pageable);

    Page<Post> findDistinctByAuthor_IdAndStatusAndTags_SlugOrderByUpdatedAtDesc(
            Long authorId,
            PostStatus status,
            String tagSlug,
            Pageable pageable
    );

    @Query(value = """
            select distinct p.*
            from posts p
            left join post_tags pt on pt.post_id = p.id
            left join tags t on t.id = pt.tag_id
            where p.status = 'PUBLISHED'
              and (:tagSlug is null or t.slug = :tagSlug)
              and (
                :searchTerm is null
                or :searchTerm = ''
                or lower(p.title) like lower(concat('%', :searchTerm, '%'))
                or lower(coalesce(p.excerpt, '')) like lower(concat('%', :searchTerm, '%'))
                or lower(p.markdown_content) like lower(concat('%', :searchTerm, '%'))
              )
            order by p.published_at desc nulls last, p.updated_at desc
            """,
            countQuery = """
            select count(distinct p.id)
            from posts p
            left join post_tags pt on pt.post_id = p.id
            left join tags t on t.id = pt.tag_id
            where p.status = 'PUBLISHED'
              and (:tagSlug is null or t.slug = :tagSlug)
              and (
                :searchTerm is null
                or :searchTerm = ''
                or lower(p.title) like lower(concat('%', :searchTerm, '%'))
                or lower(coalesce(p.excerpt, '')) like lower(concat('%', :searchTerm, '%'))
                or lower(p.markdown_content) like lower(concat('%', :searchTerm, '%'))
              )
            """,
            nativeQuery = true)
    Page<Post> searchPublishedPosts(
            @Param("searchTerm") String searchTerm,
            @Param("tagSlug") String tagSlug,
            Pageable pageable
    );

    boolean existsBySlug(String slug);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from posts where author_id = :authorId", nativeQuery = true)
    int deleteAllByAuthorIdNative(@Param("authorId") Long authorId);
}
