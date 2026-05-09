package org.example.dip2.service;

import java.util.List;
import java.util.UUID;
import org.example.dip2.dto.quiz.QuizAnswerRequest;
import org.example.dip2.dto.quiz.QuizAnswerResponse;
import org.example.dip2.dto.quiz.QuizDetailResponse;
import org.example.dip2.dto.quiz.QuizQuestionRequest;
import org.example.dip2.dto.quiz.QuizQuestionResponse;
import org.example.dip2.dto.quiz.QuizSummaryResponse;
import org.example.dip2.dto.quiz.QuizUpsertRequest;
import org.example.dip2.exception.ApiException;
import org.example.dip2.model.Answer;
import org.example.dip2.model.Question;
import org.example.dip2.model.Quiz;
import org.example.dip2.model.User;
import org.example.dip2.repository.QuizRepository;
import org.example.dip2.repository.UserRepository;
import org.example.dip2.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizService {

    private final QuizRepository quizRepository;
    private final UserRepository userRepository;

    public QuizService(QuizRepository quizRepository, UserRepository userRepository) {
        this.quizRepository = quizRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public QuizDetailResponse createQuiz(AuthenticatedUser authenticatedUser, QuizUpsertRequest request) {
        User author = loadAuthor(authenticatedUser.id());
        validateQuestionStructure(request);

        Quiz quiz = Quiz.builder()
                .title(request.title().trim())
                .author(author)
                .build();
        quiz.replaceQuestions(mapQuestions(request.questions()));

        return toDetailResponse(quizRepository.saveAndFlush(quiz));
    }

    @Transactional(readOnly = true)
    public List<QuizSummaryResponse> listOwnQuizzes(AuthenticatedUser authenticatedUser) {
        return quizRepository.findAllByAuthorIdOrderByUpdatedAtDesc(authenticatedUser.id())
                .stream()
                .map(quiz -> new QuizSummaryResponse(
                        quiz.getId().toString(),
                        quiz.getTitle(),
                        quiz.getQuestions().size(),
                        quiz.getCreatedAt(),
                        quiz.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public QuizDetailResponse getOwnQuiz(AuthenticatedUser authenticatedUser, UUID quizId) {
        return toDetailResponse(loadOwnedQuiz(authenticatedUser.id(), quizId));
    }

    @Transactional
    public QuizDetailResponse updateQuiz(AuthenticatedUser authenticatedUser, UUID quizId, QuizUpsertRequest request) {
        validateQuestionStructure(request);
        Quiz quiz = loadOwnedQuiz(authenticatedUser.id(), quizId);
        quiz.setTitle(request.title().trim());
        quiz.replaceQuestions(mapQuestions(request.questions()));
        return toDetailResponse(quizRepository.saveAndFlush(quiz));
    }

    @Transactional
    public void deleteQuiz(AuthenticatedUser authenticatedUser, UUID quizId) {
        Quiz quiz = loadOwnedQuiz(authenticatedUser.id(), quizId);
        quizRepository.delete(quiz);
    }

    private User loadAuthor(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Quiz loadOwnedQuiz(UUID authorId, UUID quizId) {
        return quizRepository.findByIdAndAuthorId(quizId, authorId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quiz not found"));
    }

    private void validateQuestionStructure(QuizUpsertRequest request) {
        for (int questionIndex = 0; questionIndex < request.questions().size(); questionIndex++) {
            QuizQuestionRequest question = request.questions().get(questionIndex);
            List<QuizAnswerRequest> answers = question.answers();
            if (answers.isEmpty() || answers.size() > 4) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Question " + (questionIndex + 1) + " must contain between 1 and 4 answers"
                );
            }
            boolean hasCorrectAnswer = answers.stream().anyMatch(QuizAnswerRequest::isCorrect);
            if (!hasCorrectAnswer) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Question " + (questionIndex + 1) + " must contain at least one correct answer"
                );
            }
        }
    }

    private List<Question> mapQuestions(List<QuizQuestionRequest> requests) {
        return requests.stream()
                .map(this::mapQuestion)
                .toList();
    }

    private Question mapQuestion(QuizQuestionRequest request) {
        Question question = Question.builder()
                .text(request.text().trim())
                .imageUrl(normalizeOptional(request.imageUrl()))
                .pointsWeight(request.pointsWeight())
                .timerOverride(request.timerOverride())
                .build();
        question.replaceAnswers(request.answers().stream().map(this::mapAnswer).toList());
        return question;
    }

    private Answer mapAnswer(QuizAnswerRequest request) {
        return Answer.builder()
                .text(request.text().trim())
                .isCorrect(request.isCorrect())
                .build();
    }

    private QuizDetailResponse toDetailResponse(Quiz quiz) {
        return new QuizDetailResponse(
                quiz.getId().toString(),
                quiz.getTitle(),
                quiz.getAuthor().getId().toString(),
                quiz.getCreatedAt(),
                quiz.getUpdatedAt(),
                quiz.getQuestions().stream().map(this::toQuestionResponse).toList()
        );
    }

    private QuizQuestionResponse toQuestionResponse(Question question) {
        return new QuizQuestionResponse(
                question.getId().toString(),
                question.getText(),
                question.getImageUrl(),
                question.getPointsWeight(),
                question.getTimerOverride(),
                question.getAnswers().stream().map(this::toAnswerResponse).toList()
        );
    }

    private QuizAnswerResponse toAnswerResponse(Answer answer) {
        return new QuizAnswerResponse(
                answer.getId().toString(),
                answer.getText(),
                Boolean.TRUE.equals(answer.getIsCorrect())
        );
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
