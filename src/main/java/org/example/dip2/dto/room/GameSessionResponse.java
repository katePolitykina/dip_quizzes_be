package org.example.dip2.dto.room;

import java.time.Instant;
import java.util.List;

public record GameSessionResponse(
        String pin,
        String hostUserId,
        String quizId,
        String quizTitle,
        int globalTimer,
        boolean cbmEnabled,
        boolean playInTeams,
        Integer configuredTeamCount,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Integer currentQuestionIndex,
        Instant questionStartedAt,
        Instant questionDeadlineAt,
        int answersCount,
        CbmSettingsResponse cbmSettings,
        CurrentQuestionResponse currentQuestion,
        List<LeaderboardEntryResponse> leaderboard,
        FinalGameReportResponse finalReport,
        List<PlayerSlotResponse> participants,
        List<TeamStateResponse> teams
) {
}
