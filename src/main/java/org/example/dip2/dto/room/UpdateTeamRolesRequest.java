package org.example.dip2.dto.room;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateTeamRolesRequest(
        @Valid @NotEmpty List<TeamRoleAssignmentRequest> assignments
) {
    public record TeamRoleAssignmentRequest(
            @NotBlank String teamId,
            @NotBlank String captainParticipantId,
            @NotBlank String analystParticipantId
    ) {
    }
}
