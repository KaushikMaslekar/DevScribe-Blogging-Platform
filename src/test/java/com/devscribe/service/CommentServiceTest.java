package com.devscribe.service;

import com.devscribe.dto.comment.CommentResponse;
import com.devscribe.dto.comment.CreateCommentRequest;
import com.devscribe.entity.*;
import com.devscribe.repository.CommentRepository;
import com.devscribe.repository.PostRepository;
import com.devscribe.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CommentServiceTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private User author;
    private User commenter;
    private Post post;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        author = userRepository.save(User.builder()
                .username("author")
                .email("author@test.com")
                .passwordHash("hashedpwd")
                .displayName("Author User")
                .role(UserRole.WRITER)
                .build());

        commenter = userRepository.save(User.builder()
                .username("commenter")
                .email("commenter@test.com")
                .passwordHash("hashedpwd")
                .displayName("Commenter User")
                .role(UserRole.WRITER)
                .build());

        post = postRepository.save(Post.builder()
                .author(author)
                .title("Test Post")
                .slug("test-post")
                .markdownContent("# Test Content")
                .status(PostStatus.PUBLISHED)
                .publishedAt(java.time.OffsetDateTime.now())
                .build());
    }

    @Test
    void testCommentCountForPost_InitiallyZero() {
        long count = commentService.getCommentCountForPost(post.getId());
        assertEquals(0, count);
    }

    @Test
    void testCommentCountForPost_AfterCreating() {
        // Create comment directly in repo (bypassing auth check)
        commentRepository.save(Comment.builder()
                .post(post)
                .author(commenter)
                .content("Test comment")
                .status(CommentStatus.ACTIVE)
                .build());

        long count = commentService.getCommentCountForPost(post.getId());
        assertEquals(1, count);
    }

    @Test
    void testCommentCountForPost_ExcludesFlaggedAndDeleted() {
        // Create active comment
        commentRepository.save(Comment.builder()
                .post(post)
                .author(commenter)
                .content("Active")
                .status(CommentStatus.ACTIVE)
                .build());

        // Create flagged comment
        commentRepository.save(Comment.builder()
                .post(post)
                .author(author)
                .content("Flagged")
                .status(CommentStatus.FLAGGED)
                .build());

        // Create deleted comment
        commentRepository.save(Comment.builder()
                .post(post)
                .author(commenter)
                .content("Deleted")
                .status(CommentStatus.DELETED)
                .build());

        long count = commentService.getCommentCountForPost(post.getId());
        assertEquals(1, count); // Only active counted
    }

    @Test
    void testGetComments_ReturnsThreadedStructure() {
        // Create parent comment
        Comment parent = commentRepository.save(Comment.builder()
                .post(post)
                .author(commenter)
                .content("Parent comment")
                .status(CommentStatus.ACTIVE)
                .build());

        // Create reply
        commentRepository.save(Comment.builder()
                .post(post)
                .author(author)
                .parentComment(parent)
                .content("Reply")
                .status(CommentStatus.ACTIVE)
                .build());

        var threadResponse = commentService.getCommentsForPost(post.getId(), PageRequest.of(0, 20));
        assertEquals(1, threadResponse.getContent().size());
        assertEquals(1, threadResponse.getContent().get(0).replies().size());
    }

    @Test
    void testFlagComment_ChangeStatus() {
        Comment comment = commentRepository.save(Comment.builder()
                .post(post)
                .author(commenter)
                .content("To flag")
                .status(CommentStatus.ACTIVE)
                .build());

        commentService.flagComment(post.getId(), comment.getId());

        Comment flagged = commentRepository.findById(comment.getId()).orElse(null);
        assertNotNull(flagged);
        assertEquals(CommentStatus.FLAGGED, flagged.getStatus());
    }

    @Test
    void testFlagComment_WrongPost_Fails() {
        Post otherPost = postRepository.save(Post.builder()
                .author(author)
                .title("Other Post")
                .slug("other-post")
                .markdownContent("# Other")
                .status(PostStatus.PUBLISHED)
                .build());

        Comment comment = commentRepository.save(Comment.builder()
                .post(post)
                .author(commenter)
                .content("Test")
                .status(CommentStatus.ACTIVE)
                .build());

        assertThrows(ResponseStatusException.class,
            () -> commentService.flagComment(otherPost.getId(), comment.getId()));
    }

    @Test
    void testFlagComment_AlreadyDeleted_Fails() {
        Comment comment = commentRepository.save(Comment.builder()
                .post(post)
                .author(commenter)
                .content("Test")
                .status(CommentStatus.DELETED)
                .build());

        assertThrows(ResponseStatusException.class,
            () -> commentService.flagComment(post.getId(), comment.getId()));
    }
}

