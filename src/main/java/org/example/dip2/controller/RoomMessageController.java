package org.example.dip2.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import org.example.dip2.dto.ws.ModerationCommandRequest;
import org.example.dip2.dto.ws.StartGameRequest;
import org.example.dip2.dto.ws.TeamAnswerSelectionRequest;
import org.example.dip2.security.AuthenticatedUser;
import org.example.dip2.service.GameLoopService;
import org.example.dip2.service.RoomService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class RoomMessageController {

    private final RoomService roomService;
    private final GameLoopService gameLoopService;

    public RoomMessageController(RoomService roomService, GameLoopService gameLoopService) {
        this.roomService = roomService;
        this.gameLoopService = gameLoopService;
    }

    @MessageMapping("/rooms/{pin}/moderation")
    public void moderateRoom(
            @DestinationVariable String pin,
            @Valid ModerationCommandRequest request,
            Principal principal
    ) {
        AuthenticatedUser authenticatedUser = extractAuthenticatedUser(principal);
        if (request.command() == ModerationCommandRequest.ModerationCommand.TOGGLE_PAUSE) {
            gameLoopService.togglePause(pin, authenticatedUser);
            return;
        }
        if (request.command() == ModerationCommandRequest.ModerationCommand.END_GAME) {
            gameLoopService.endGame(pin, authenticatedUser);
            return;
        }
        roomService.kickPlayer(pin, authenticatedUser, request.targetParticipantId());
    }

    @MessageMapping("/rooms/{pin}/game/start")
    public void startGame(
            @DestinationVariable String pin,
            @Valid StartGameRequest request,
            Principal principal
    ) {
        gameLoopService.startGame(pin, extractAuthenticatedUser(principal), request);
    }

    @MessageMapping("/rooms/{pin}/game/advance")
    public void advanceGame(
            @DestinationVariable String pin,
            Principal principal
    ) {
        gameLoopService.advanceFromResults(pin, extractAuthenticatedUser(principal));
    }

    @MessageMapping("/rooms/{pin}/teams/{teamId}/selection")
    public void selectTeamAnswer(
            @DestinationVariable String pin,
            @DestinationVariable String teamId,
            @Valid TeamAnswerSelectionRequest request,
            Principal principal
    ) {
        gameLoopService.selectTeamAnswer(pin, teamId, extractAuthenticatedUser(principal), request.answerId());
    }

    @MessageMapping("/rooms/{pin}/teams/{teamId}/confirm")
    public void confirmTeamAnswer(
            @DestinationVariable String pin,
            @DestinationVariable String teamId,
            @Valid TeamAnswerSelectionRequest request,
            Principal principal
    ) {
        gameLoopService.confirmTeamAnswer(pin, teamId, extractAuthenticatedUser(principal), request.answerId(), request.confidenceLevel());
    }

    @MessageMapping("/rooms/{pin}/teams/{teamId}/sort-answers")
    public void sortAnswers(
            @DestinationVariable String pin,
            @DestinationVariable String teamId,
            Principal principal
    ) {
        gameLoopService.useSortAnswers(pin, teamId, extractAuthenticatedUser(principal));
    }

    private AuthenticatedUser extractAuthenticatedUser(Principal principal) {
        if (principal instanceof org.springframework.security.core.Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }
        throw new org.example.dip2.exception.ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "WebSocket principal is missing");
    }
}
