package org.example.dip2;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class QuizManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void userCanCreateListUpdateAndDeleteOwnQuiz() throws Exception {
        String token = registerUserAndGetToken("quiz-owner@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/quizzes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validQuizPayload("Capitals Quiz")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Capitals Quiz"))
                .andExpect(jsonPath("$.questions.length()").value(1))
                .andExpect(jsonPath("$.questions[0].answers.length()").value(3))
                .andReturn();

        JsonNode createBody = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String quizId = createBody.get("id").asText();

        mockMvc.perform(get("/api/quizzes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(quizId))
                .andExpect(jsonPath("$[0].questionCount").value(1));

        mockMvc.perform(get("/api/quizzes/{id}", quizId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(quizId))
                .andExpect(jsonPath("$.questions[0].text").value("What is the capital of France?"));

        mockMvc.perform(put("/api/quizzes/{id}", quizId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validQuizPayload("Updated Capitals Quiz")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Capitals Quiz"));

        mockMvc.perform(delete("/api/quizzes/{id}", quizId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/quizzes/{id}", quizId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void guestCannotAccessQuizEndpoints() throws Exception {
        String token = issueGuestToken();

        mockMvc.perform(get("/api/quizzes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingQuizFailsWhenQuestionHasNoCorrectAnswer() throws Exception {
        String token = registerUserAndGetToken("validation@example.com");

        mockMvc.perform(post("/api/quizzes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Broken Quiz",
                                  "questions": [
                                    {
                                      "text": "Choose the right answer",
                                      "pointsWeight": 100,
                                      "answers": [
                                        { "text": "A", "isCorrect": false },
                                        { "text": "B", "isCorrect": false }
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Question 1 must contain at least one correct answer"));
    }

    @Test
    void userCannotReadAnotherUsersQuiz() throws Exception {
        String ownerToken = registerUserAndGetToken("owner@example.com");
        String otherToken = registerUserAndGetToken("other@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/quizzes")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validQuizPayload("Private Quiz")))
                .andExpect(status().isCreated())
                .andReturn();

        String quizId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/quizzes/{id}", quizId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    private String registerUserAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "displayName": "Quiz Owner"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String issueGuestToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "Quiz Guest"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String validQuizPayload(String title) {
        return """
                {
                  "title": "%s",
                  "questions": [
                    {
                      "text": "What is the capital of France?",
                      "pointsWeight": 100,
                      "timerOverride": 30,
                      "answers": [
                        { "text": "Paris", "isCorrect": true },
                        { "text": "Berlin", "isCorrect": false },
                        { "text": "Madrid", "isCorrect": false }
                      ]
                    }
                  ]
                }
                """.formatted(title);
    }
}
