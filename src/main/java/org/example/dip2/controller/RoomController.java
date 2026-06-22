package org.example.dip2.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dip2.dto.room.AutoDistributeTeamsRequest;
import org.example.dip2.dto.room.CreateRoomRequest;
import org.example.dip2.dto.room.GameSessionResponse;
import org.example.dip2.dto.room.UpdateTeamsRequest;
import org.example.dip2.dto.room.UpdateTeamRolesRequest;
import org.example.dip2.security.AuthenticatedUser;
import org.example.dip2.service.FinalReportExportService;
import org.example.dip2.service.RoomService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final FinalReportExportService finalReportExportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameSessionResponse createRoom(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateRoomRequest request
    ) {
        return roomService.createRoom(authenticatedUser, request);
    }

    @PostMapping("/{pin}/join")
    public GameSessionResponse joinRoom(
            @PathVariable String pin,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return roomService.joinRoom(pin, authenticatedUser);
    }

    @GetMapping("/{pin}")
    public GameSessionResponse getRoom(
            @PathVariable String pin,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return roomService.getRoom(pin, authenticatedUser);
    }

    @GetMapping("/{pin}/final-report.xlsx")
    public ResponseEntity<byte[]> downloadFinalReport(
            @PathVariable String pin,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        byte[] workbook = finalReportExportService.export(roomService.getFinalReport(pin, authenticatedUser));
        String normalizedPin = pin.toUpperCase();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(normalizedPin + "-detailed-result.xlsx")
                        .build()
                        .toString())
                .body(workbook);
    }

    @DeleteMapping("/{pin}/leave")
    public GameSessionResponse leaveRoom(
            @PathVariable String pin,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return roomService.leaveRoom(pin, authenticatedUser);
    }

    @PostMapping("/{pin}/teams/auto-distribute")
    public GameSessionResponse autoDistribute(
            @PathVariable String pin,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody AutoDistributeTeamsRequest request
    ) {
        return roomService.autoDistribute(pin, authenticatedUser, request);
    }

    @PatchMapping("/{pin}/teams")
    public GameSessionResponse updateTeams(
            @PathVariable String pin,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdateTeamsRequest request
    ) {
        return roomService.updateTeams(pin, authenticatedUser, request);
    }

    @PatchMapping("/{pin}/teams/roles")
    public GameSessionResponse updateRoles(
            @PathVariable String pin,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdateTeamRolesRequest request
    ) {
        return roomService.updateRoles(pin, authenticatedUser, request);
    }
}
