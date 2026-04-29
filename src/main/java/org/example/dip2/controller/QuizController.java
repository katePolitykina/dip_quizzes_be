package org.example.dip2.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.dip2.dto.quiz.QuizDetailResponse;
import org.example.dip2.dto.quiz.QuizSummaryResponse;
import org.example.dip2.dto.quiz.QuizUpsertRequest;
import org.example.dip2.security.AuthenticatedUser;
import org.example.dip2.service.QuizService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDetailResponse createQuiz(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody QuizUpsertRequest request
    ) {
        return quizService.createQuiz(authenticatedUser, request);
    }

    @GetMapping
    public List<QuizSummaryResponse> listQuizzes(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return quizService.listOwnQuizzes(authenticatedUser);
    }

    @GetMapping("/{id}")
    public QuizDetailResponse getQuiz(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID id
    ) {
        return quizService.getOwnQuiz(authenticatedUser, id);
    }

    @PutMapping("/{id}")
    public QuizDetailResponse updateQuiz(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID id,
            @Valid @RequestBody QuizUpsertRequest request
    ) {
        return quizService.updateQuiz(authenticatedUser, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuiz(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID id
    ) {
        quizService.deleteQuiz(authenticatedUser, id);
    }
}
