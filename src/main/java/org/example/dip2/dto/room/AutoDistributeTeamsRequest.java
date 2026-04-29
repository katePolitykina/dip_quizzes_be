package org.example.dip2.dto.room;

import jakarta.validation.constraints.Min;

public record AutoDistributeTeamsRequest(
        @Min(1) int teamCount
) {
}
