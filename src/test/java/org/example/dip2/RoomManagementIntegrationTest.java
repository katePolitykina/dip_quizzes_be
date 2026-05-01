package org.example.dip2;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class RoomManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void hostCanCreateRoomJoinPlayersDistributeTeamsAndUpdateRoles() throws Exception {
        String hostToken = registerUserAndGetToken("host-room@example.com", "Host User");
        String userToken = registerUserAndGetToken("player-room@example.com", "Player User");
        String guestOneToken = issueGuestToken("Guest One");
        String guestTwoToken = issueGuestToken("Guest Two");

        MvcResult createResult = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "globalTimer": 45,
                                  "cbmEnabled": true,
                                  "playInTeams": true,
                                  "teamCount": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pin").exists())
                .andExpect(jsonPath("$.pin").value(Matchers.matchesPattern("^[A-Z0-9]{6}$")))
                .andExpect(jsonPath("$.playInTeams").value(true))
                .andExpect(jsonPath("$.configuredTeamCount").value(2))
                .andExpect(jsonPath("$.participants.length()").value(0))
                .andExpect(jsonPath("$.teams.length()").value(2))
                .andExpect(jsonPath("$.teams[0].name").value("Team 1"))
                .andExpect(jsonPath("$.teams[0].participantIds.length()").value(0))
                .andExpect(jsonPath("$.teams[1].name").value("Team 2"))
                .andExpect(jsonPath("$.teams[1].participantIds.length()").value(0))
                .andReturn();

        JsonNode createdRoom = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String pin = createdRoom.get("pin").asText();

        mockMvc.perform(post("/api/rooms/{pin}/join", pin)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(2));

        mockMvc.perform(post("/api/rooms/{pin}/join", pin)
                        .header("Authorization", "Bearer " + guestOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(3));

        mockMvc.perform(post("/api/rooms/{pin}/join", pin)
                        .header("Authorization", "Bearer " + guestTwoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(4));

        MvcResult distributeResult = mockMvc.perform(post("/api/rooms/{pin}/teams/auto-distribute", pin)
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teamCount": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams.length()").value(2))
                .andExpect(jsonPath("$.teams[0].participantIds.length()").value(2))
                .andExpect(jsonPath("$.teams[1].participantIds.length()").value(2))
                .andExpect(jsonPath("$.participants[?(@.teamRole == 'CAPTAIN')]").isNotEmpty())
                .andExpect(jsonPath("$.participants[?(@.teamRole == 'ANALYST')]").isNotEmpty())
                .andReturn();

        JsonNode distributedRoom = objectMapper.readTree(distributeResult.getResponse().getContentAsString());
        JsonNode firstTeam = distributedRoom.get("teams").get(0);
        Iterator<JsonNode> firstTeamMembers = firstTeam.get("participantIds").elements();
        String firstMember = firstTeamMembers.next().asText();
        String secondMember = firstTeamMembers.next().asText();

        mockMvc.perform(patch("/api/rooms/{pin}/teams/roles", pin)
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assignments": [
                                    {
                                      "teamId": "%s",
                                      "captainParticipantId": "%s",
                                      "analystParticipantId": "%s"
                                    }
                                  ]
                                }
                                """.formatted(firstTeam.get("teamId").asText(), secondMember, firstMember)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[0].captainParticipantId").value(secondMember))
                .andExpect(jsonPath("$.teams[0].analystParticipantId").value(firstMember));
    }

    @Test
    void guestCannotCreateRooms() throws Exception {
        String guestToken = issueGuestToken("Guest Host");

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "globalTimer": 30,
                                  "cbmEnabled": false,
                                  "playInTeams": false,
                                  "teamCount": null
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void soloRoomAutoCreatesOneTeamPerParticipant() throws Exception {
        String hostToken = registerUserAndGetToken("solo-host@example.com", "Solo Host");
        String guestToken = issueGuestToken("Solo Guest");

        MvcResult createResult = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "globalTimer": 30,
                                  "cbmEnabled": false,
                                  "playInTeams": false,
                                  "teamCount": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playInTeams").value(false))
                .andExpect(jsonPath("$.configuredTeamCount").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.teams.length()").value(0))
                .andExpect(jsonPath("$.participants.length()").value(0))
                .andReturn();

        String pin = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("pin").asText();

        mockMvc.perform(post("/api/rooms/{pin}/join", pin)
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams.length()").value(1))
                .andExpect(jsonPath("$.teams[0].participantIds.length()").value(1))
                .andExpect(jsonPath("$.participants[0].teamRole").value("CAPTAIN"));
    }

    @Test
    void onePlayerTeamCanUseSameParticipantAsCaptainAndAnalyst() throws Exception {
        String hostToken = registerUserAndGetToken("one-team-host@example.com", "Host User");
        String guestOneToken = issueGuestToken("Guest One");
        String guestTwoToken = issueGuestToken("Guest Two");

        MvcResult createResult = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "globalTimer": 45,
                                  "cbmEnabled": true,
                                  "playInTeams": true,
                                  "teamCount": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String pin = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("pin").asText();

        mockMvc.perform(post("/api/rooms/{pin}/join", pin)
                        .header("Authorization", "Bearer " + guestOneToken))
                .andExpect(status().isOk());

        MvcResult joinResult = mockMvc.perform(post("/api/rooms/{pin}/join", pin)
                        .header("Authorization", "Bearer " + guestTwoToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode roomAfterJoin = objectMapper.readTree(joinResult.getResponse().getContentAsString());
        String firstParticipantId = roomAfterJoin.get("participants").get(0).get("participantId").asText();
        String secondParticipantId = roomAfterJoin.get("participants").get(1).get("participantId").asText();

        MvcResult distributeResult = mockMvc.perform(post("/api/rooms/{pin}/teams/auto-distribute", pin)
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teamCount": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams.length()").value(2))
                .andExpect(jsonPath("$.teams[0].participantIds.length()").value(1))
                .andExpect(jsonPath("$.teams[1].participantIds.length()").value(1))
                .andReturn();

        JsonNode distributedRoom = objectMapper.readTree(distributeResult.getResponse().getContentAsString());
        String firstTeamId = distributedRoom.get("teams").get(0).get("teamId").asText();
        String secondTeamId = distributedRoom.get("teams").get(1).get("teamId").asText();

        mockMvc.perform(patch("/api/rooms/{pin}/teams/roles", pin)
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assignments": [
                                    {
                                      "teamId": "%s",
                                      "captainParticipantId": "%s",
                                      "analystParticipantId": "%s"
                                    },
                                    {
                                      "teamId": "%s",
                                      "captainParticipantId": "%s",
                                      "analystParticipantId": "%s"
                                    }
                                  ]
                                }
                                """.formatted(
                                        firstTeamId, firstParticipantId, firstParticipantId,
                                        secondTeamId, secondParticipantId, secondParticipantId
                                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[0].captainParticipantId").value(firstParticipantId))
                .andExpect(jsonPath("$.teams[0].analystParticipantId").value(firstParticipantId))
                .andExpect(jsonPath("$.teams[1].captainParticipantId").value(secondParticipantId))
                .andExpect(jsonPath("$.teams[1].analystParticipantId").value(secondParticipantId));
    }

    private String registerUserAndGetToken(String email, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "displayName": "%s"
                                }
                                """.formatted(email, displayName)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String issueGuestToken(String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "%s"
                                }
                                """.formatted(nickname)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
