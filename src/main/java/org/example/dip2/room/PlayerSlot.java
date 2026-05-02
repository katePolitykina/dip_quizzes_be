package org.example.dip2.room;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerSlot {

    private String participantId;
    private String userId;
    private String displayName;
    private String avatarUrl;
    private String provider;
    private boolean guest;
    private String teamId;
    private TeamRole teamRole;
    private String selectedAnswerId;
    private long joinedAtEpochMillis;
    private Long lastAnsweredAtEpochMillis;

    @Builder.Default
    private List<PlayerQuestionAnswer> questionAnswers = new ArrayList<>();
}
