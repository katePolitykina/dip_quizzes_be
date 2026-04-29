package org.example.dip2.room;

import java.time.Instant;
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
public class FinalGameReport {

    private String quizId;
    private String quizTitle;
    private Instant generatedAt;

    @Builder.Default
    private List<FinalTeamReport> teams = new ArrayList<>();
}
