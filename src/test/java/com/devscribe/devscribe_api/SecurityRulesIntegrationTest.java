package com.devscribe.devscribe_api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityRulesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicPostsEndpointIsAccessible() throws Exception {
        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk());
    }

    @Test
    void healthEndpointIsAccessible() throws Exception {
        mockMvc.perform(get("/health/live"))
                .andExpect(status().isOk());
    }

    @Test
    void creatingPostWithoutAuthIsRejected() throws Exception {
        mockMvc.perform(post("/posts")
                .contentType("application/json")
                .content("{\"title\":\"Test\",\"markdownContent\":\"Body\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void deletingUserWithoutAuthIsRejected() throws Exception {
        mockMvc.perform(delete("/users/1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listingSeriesWithoutAuthIsRejected() throws Exception {
        mockMvc.perform(get("/series"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listingSeriesPostsWithoutAuthIsRejected() throws Exception {
        mockMvc.perform(get("/series/1/posts"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void creatingSeriesWithoutAuthIsRejected() throws Exception {
        mockMvc.perform(post("/series")
                .contentType("application/json")
                .content("{\"title\":\"Java Mastery\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void attachingSeriesPostWithoutAuthIsRejected() throws Exception {
        mockMvc.perform(post("/series/1/posts")
                .contentType("application/json")
                .content("{\"postId\":1}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void detachingSeriesPostWithoutAuthIsRejected() throws Exception {
        mockMvc.perform(delete("/series/1/posts/1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void movingSeriesPostWithoutAuthIsRejected() throws Exception {
        mockMvc.perform(patch("/series/1/posts/1/move")
                .contentType("application/json")
                .content("{\"sortOrder\":1}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void reorderingSeriesPostsWithoutAuthIsRejected() throws Exception {
        mockMvc.perform(put("/series/1/posts/reorder")
                .contentType("application/json")
                .content("{\"postIds\":[1,2]}"))
                .andExpect(status().is4xxClientError());
    }
}
