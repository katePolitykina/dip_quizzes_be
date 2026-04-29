package org.example.dip2.dto.room;

public record LeaderboardEntryResponse(
        String teamId,
        String teamName,
        double score,
        int rank
) {
}
