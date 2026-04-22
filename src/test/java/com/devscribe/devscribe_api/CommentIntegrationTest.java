package com.devscribe.devscribe_api;

import com.devscribe.dto.comment.CommentResponse;
import com.devscribe.dto.comment.CommentThreadResponse;
import com.devscribe.dto.comment.CreateCommentRequest;
import com.devscribe.entity.*;
import com.devscribe.repository.CommentRepository;
import com.devscribe.repository.PostRepository;
import com.devscribe.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class CommentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    private User author;
    private User commenter;
    private Post post;
    private String authorToken;
    private String commenterToken;

    @BeforeEach
    void setUp() {
        // Clean up
        commentRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        // Create users
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

        // Create post
        post = postRepository.save(Post.builder()
                .author(author)
                .title("Test Post")
                .slug("test-post")
                .markdownContent("# Test Content")
                .status(PostStatus.PUBLISHED)
                .publishedAt(java.time.OffsetDateTime.now())
                .build());

        // Generate tokens (these would normally come from auth flow)
        authorToken = "Bearer dummy-token-author";
        commenterToken = "Bearer dummy-token-commenter";
    }

    @Test
    void testCreateComment_Success() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest(
                "This is a test comment",
                null
        );

        mockMvc.perform(post("/posts/{postId}/comments", post.getId())
                .header("Authorization", commenterToken)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.postId").value(post.getId()))
                .andExpect(jsonPath("$.content").value("This is a test comment"))
                .andExpect(jsonPath("$.author.username").value("commenter"))
                .andExpect(jsonPath("$.isAuthor").value(true));
    }

    @Test
    void testCreateCommentReply_Success() throws Exception {
        // Create parent comment
        Comment parent = commentRepository.save(Comment.builder()
                .post(post)
                .author(commenter)
                .content("Parent comment")
                .status(CommentStatus.ACTIVE)
                .build());

        CreateCommentRequest request = new CreateCommentRequest(
                "This is a reply",
                parent.getId()
        );

        mockMvc.perform(post("/posts/{postId}/comments", post.getId())
                .header("Authorization", commenterToken)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentCommentId").value(parent.getId()))
                .andExpect(jsonPath("$.content").value("This is a reply"));
    }

    @Test
    void testCreateComment_BlankContent_Fails() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest("", null);

        mockMvc.perform(post("/posts/{postId}/comments", post.getId())
                .header("Authorization", commenterToken)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetComments_Success() throws Exception {
        // Create multiple comments
        commentRepository.save(Comment.builder()
                .post(post)
                .author(commenter)
                .content("First comment")
                .status(CommentStatus.ACTIVE)
                .build());

        commentRepository.save(Comment.builder()
                .post(post)
                .author(author)
                .content("Second comment")
                .status(CommentStatus.ACTIVE)
                .build());

        mockMvc.perform(get("/posts/{postId}/comments", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void testGetComments_WithReplies_NestedCorrectly() throws Exception {
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
                .content("Reply to parent")
                .status(CommentStatus.ACTIVE)
                .build());

        mockMvc.perform(get("/posts/{postId}/comments", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].comment.content").value("Parent comment"))
                .andExpect(jsonPath("$.content[0].replies", hasSize(1)))
                .andExpect(jsonPath("$.content[0].replies[0].comment.content").value("Reply to parent"));
    }

    @Test
    void testGetCommentCount_Success() throws Exception {
        commentRepository.save(Comment.builder()
                .post(post)
                .author(commenter)
                .content("Comment 1")
                .status(CommentStatus.ACTIVE)
                .build());

        commentRepository.save(Comment.builder()
                .post(post)
                .author(author)
                .content("Comment 2")
                .status(CommentStatus.ACTIVE)
                .build());

        mockMvc.perform(get("/posts/{postId}/comments/count", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(post.getId()))
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void testDeleteComment_AsAuthor_Success() throws Exception {
        Comment comment = commentRepository.save(Comment.builder()
                .post(post)
                .author(commenter)
                .content("To be deleted")
                .status(CommentStatus.ACTIVE)
                .build());

        mockMvc.perform(delete("/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                .header("Authorization", commenterToken))
                .andExpect(status().isNoContent());

        // Verify soft delete
        Comment deleted = commentRepository.findById(comment.getId()).orElse(null);
        assertNotNull(deleted);
        assertEquals(CommentStatus.DELETED, deleted.getStatus());
    }

    @Test
    void testDeleteComment_AsPostOwner_Success() throws Exception {
        Comment comment = commentRepository.save(Comment.builder()
                .post(post)
                .author(commenter)
                .content("To be deleted")
                .status(CommentStatus.ACTIVE)
                .build());

        // Post owner (author) deletes someone else's comment
        mockMvc.perform(delete("/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                .header("Authorization", authorToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteComment_Unauthorized_Fails() throws Exception {
        User otherUser = userRepository.save(User.builder()
                .username("other")
                .email("other@test.com")
                .passwordHash("hashedpwd")
                .displayName("Other User")
                .role(UserRole.WRITER)
                .build());

        Comment comment = commentRepository.save(Comment.builder()
                .post(post)
                .author(commenter)
                .content("Cannot delete")
                .status(CommentStatus.ACTIVE)
                .build());

        String otherToken = "Bearer dummy-token-other";

        mockMvc.perform(delete("/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                .header("Authorization", otherToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testFlagComment_Success() throws Exception {
        Comment comment = commentRepository.save(Comment.builder()
                .post(post)
                .author(commenter)
                .content("Spam comment")
                .status(CommentStatus.ACTIVE)
                .build());

        mockMvc.perform(post("/posts/{postId}/comments/{commentId}/flag", post.getId(), comment.getId()))
                .andExpect(status().isOk());

        // Verify status changed
        Comment flagged = commentRepository.findById(comment.getId()).orElse(null);
        assertNotNull(flagged);
        assertEquals(CommentStatus.FLAGGED, flagged.getStatus());
    }

    @Test
    void testGetComments_ExcludesFlaggedAndDeleted() throws Exception {
        // Active comment
        commentRepository.save(Comment.builder()
                .post(post)
                .author(commenter)
                .content("Active comment")
                .status(CommentStatus.ACTIVE)
                .build());

        // Flagged comment
        commentRepository.save(Comment.builder()
                .post(post)
                .author(author)
                .content("Flagged comment")
                .status(CommentStatus.FLAGGED)
                .build());

        // Deleted comment
        commentRepository.save(Comment.builder()
                .post(post)
                .author(commenter)
                .content("Deleted comment")
                .status(CommentStatus.DELETED)
                .build());

        mockMvc.perform(get("/posts/{postId}/comments", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].comment.content").value("Active comment"));
    }
}

