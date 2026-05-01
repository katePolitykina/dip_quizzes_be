package org.example.dip2.dto.room;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateTeamsRequest(
        @Valid @NotEmpty List<TeamAssignmentRequest> assignments
) {
    public record TeamAssignmentRequest(
            @NotBlank String teamId,
            @Valid List<String> participantIds
    ) {
    }
}
