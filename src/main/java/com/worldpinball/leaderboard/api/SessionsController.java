package com.worldpinball.leaderboard.api;

import com.worldpinball.leaderboard.api.dtos.SessionContracts.*;
import com.worldpinball.leaderboard.application.features.gamesessions.abortsession.AbortSessionHandler;
import com.worldpinball.leaderboard.application.features.gamesessions.abortsession.AbortSessionResult;
import com.worldpinball.leaderboard.application.features.gamesessions.abortsession.commands.AbortSessionCommand;
import com.worldpinball.leaderboard.application.features.gamesessions.addplayer.AddPlayerHandler;
import com.worldpinball.leaderboard.application.features.gamesessions.addplayer.AddPlayerResult;
import com.worldpinball.leaderboard.application.features.gamesessions.addplayer.commands.AddPlayerCommand;
import com.worldpinball.leaderboard.application.features.gamesessions.creategamesession.CreateGameSessionHandler;
import com.worldpinball.leaderboard.application.features.gamesessions.creategamesession.CreateGameSessionResult;
import com.worldpinball.leaderboard.application.features.gamesessions.creategamesession.commands.CreateGameSessionCommand;
import com.worldpinball.leaderboard.application.features.gamesessions.getlobby.GetLobbyHandler;
import com.worldpinball.leaderboard.application.features.gamesessions.getlobby.GetLobbyResult;
import com.worldpinball.leaderboard.application.features.gamesessions.getlobby.queries.GetLobbyQuery;
import com.worldpinball.leaderboard.application.features.gamesessions.kickplayer.KickPlayerHandler;
import com.worldpinball.leaderboard.application.features.gamesessions.kickplayer.KickPlayerResult;
import com.worldpinball.leaderboard.application.features.gamesessions.kickplayer.commands.KickPlayerCommand;
import com.worldpinball.leaderboard.application.features.gamesessions.launchsession.LaunchSessionHandler;
import com.worldpinball.leaderboard.application.features.gamesessions.launchsession.LaunchSessionResult;
import com.worldpinball.leaderboard.application.features.gamesessions.launchsession.commands.LaunchSessionCommand;
import com.worldpinball.leaderboard.application.features.gamesessions.recordballscore.RecordBallScoreHandler;
import com.worldpinball.leaderboard.application.features.gamesessions.recordballscore.RecordBallScoreResult;
import com.worldpinball.leaderboard.application.features.gamesessions.recordballscore.commands.RecordBallScoreCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionsController {

    private final CreateGameSessionHandler createGameSessionHandler;
    private final GetLobbyHandler getLobbyHandler;
    private final AddPlayerHandler addPlayerHandler;
    private final KickPlayerHandler kickPlayerHandler;
    private final LaunchSessionHandler launchSessionHandler;
    private final AbortSessionHandler abortSessionHandler;
    private final RecordBallScoreHandler recordBallScoreHandler;

    @PostMapping("/create")
    public ResponseEntity<?> createGameSession(
        @RequestBody CreateGameSessionRequest request,
        @RequestHeader(value = "X-Device-Id", required = false) UUID headerDeviceId
    ) {
        UUID effectiveDeviceId = request.deviceId() != null ? request.deviceId() : headerDeviceId;
        if (effectiveDeviceId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "DeviceId must be provided."));
        }

        var command = new CreateGameSessionCommand(
            effectiveDeviceId,
            request.modeCode(),
            request.clientVersion()
        );

        CreateGameSessionResult result = createGameSessionHandler.handle(command);

        return switch (result) {
            case CreateGameSessionResult.Success s -> ResponseEntity.ok(
                new CreateGameSessionResponse(s.sessionId(), s.nonce(), s.createdAtUtc().getEpochSecond())
            );
            case CreateGameSessionResult.ModeNotFound m -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Mode '" + m.modeCode() + "' not found or inactive."));
            case CreateGameSessionResult.DeviceNotFound d -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Device '" + d.deviceId() + "' not found."));
            case CreateGameSessionResult.DeviceNotActive d -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Device '" + d.deviceId() + "' is not active."));
            case CreateGameSessionResult.MachineNotAttached d -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Device '" + d.deviceId() + "' has no active machine attached."));
        };
    }

    @GetMapping("/{sessionId}/lobby")
    public ResponseEntity<?> getLobby(@PathVariable UUID sessionId) {
        GetLobbyResult result = getLobbyHandler.handle(new GetLobbyQuery(sessionId));

        return switch (result) {
            case GetLobbyResult.Success s -> ResponseEntity.ok(s.lobby());
            case GetLobbyResult.SessionNotFound s -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Session '" + s.sessionId() + "' not found."));
        };
    }

    @PostMapping("/{sessionId}/players")
    public ResponseEntity<?> addPlayer(
        @PathVariable UUID sessionId,
        @RequestBody AddPlayerRequest request
    ) {
        var command = new AddPlayerCommand(
            sessionId,
            request.slotNumber(),
            request.userId(),
            request.guestInitials(),
            request.requiresRegisteredUser()
        );

        AddPlayerResult result = addPlayerHandler.handle(command);

        return switch (result) {
            case AddPlayerResult.Success s -> ResponseEntity.ok(
                new AddPlayerResponse(s.sessionId(), s.slotNumber())
            );
            case AddPlayerResult.SessionNotFound s -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Session '" + s.sessionId() + "' not found."));
            case AddPlayerResult.SessionNotJoinable s -> ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Session '" + s.sessionId() + "' is not joinable."));
            case AddPlayerResult.InvalidSlot s -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Slot '" + s.slotNumber() + "' is invalid."));
            case AddPlayerResult.SlotAlreadyOccupied s -> ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Slot '" + s.slotNumber() + "' is already occupied."));
            case AddPlayerResult.RegisteredUserRequired s -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Registered user is required in session."));
            case AddPlayerResult.UserAlreadyRegistered u -> ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "User '" + u.userId() + "' is already registered in session."));
        };
    }

    @DeleteMapping("/{sessionId}/players/{slotNumber}")
    public ResponseEntity<?> kickPlayer(
        @PathVariable UUID sessionId,
        @PathVariable int slotNumber
    ) {
        var command = new KickPlayerCommand(sessionId, slotNumber);
        KickPlayerResult result = kickPlayerHandler.handle(command);

        return switch (result) {
            case KickPlayerResult.Success s -> ResponseEntity.ok(
                new KickPlayerResponse(s.sessionId(), s.slotNumber())
            );
            case KickPlayerResult.SessionNotFound s -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Session '" + s.sessionId() + "' not found."));
            case KickPlayerResult.PlayerNotFound s -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Player not found on slot '" + s.slotNumber() + "'."));
            case KickPlayerResult.InvalidSlot s -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Slot '" + s.slotNumber() + "' is invalid."));
            case KickPlayerResult.CannotRemovePlayerActiveSession s -> ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Cannot kick player in active session '" + s.sessionId() + "'."));
        };
    }

    @PostMapping("/{sessionId}/launch")
    public ResponseEntity<?> launchSession(@PathVariable UUID sessionId) {
        var command = new LaunchSessionCommand(sessionId);
        LaunchSessionResult result = launchSessionHandler.handle(command);

        return switch (result) {
            case LaunchSessionResult.Success s -> ResponseEntity.ok(
                new LaunchSessionResponse(s.sessionId())
            );
            case LaunchSessionResult.SessionNotFound s -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Session '" + s.sessionId() + "' not found."));
            case LaunchSessionResult.CannotStartSession s -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", s.message()));
        };
    }

    @PostMapping("/{sessionId}/abort")
    public ResponseEntity<?> abortSession(@PathVariable UUID sessionId) {
        var command = new AbortSessionCommand(sessionId);
        AbortSessionResult result = abortSessionHandler.handle(command);

        return switch (result) {
            case AbortSessionResult.Success s -> ResponseEntity.ok(
                new AbortSessionResponse(s.sessionId())
            );
            case AbortSessionResult.SessionNotFound s -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Session '" + s.sessionId() + "' not found."));
            case AbortSessionResult.CannotAbortSession s -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", s.reason()));
        };
    }

    @PostMapping("/{sessionId}/balls")
    public ResponseEntity<?> recordBallScore(
        @PathVariable UUID sessionId,
        @RequestBody RecordBallScoreRequest request
    ) {
        var command = new RecordBallScoreCommand(
            sessionId,
            request.deviceId(),
            request.slotNumber(),
            request.ballNumber(),
            request.score()
        );

        RecordBallScoreResult result = recordBallScoreHandler.handle(command);

        return switch (result) {
            case RecordBallScoreResult.Success s -> ResponseEntity.ok(
                new RecordBallScoreResponse(s.sessionId(), s.slotNumber(), s.ballNumber(), s.score())
            );
            case RecordBallScoreResult.SessionNotFound s -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Session '" + s.sessionId() + "' not found."));
            case RecordBallScoreResult.DeviceMismatch d -> ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Device '" + d.actualDeviceId() + "' mismatch (expected '" + d.expectedDeviceId() + "')."));
            case RecordBallScoreResult.CannotRecordBallScore c -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", c.reason()));
        };
    }
}
