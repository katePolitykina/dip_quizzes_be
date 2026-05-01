package org.example.dip2.dto.room;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateRoomRequest(
        @Min(10) @Max(120) int globalTimer,
        @NotNull Boolean cbmEnabled,
        @NotNull Boolean playInTeams,
        Integer teamCount
) {
}
