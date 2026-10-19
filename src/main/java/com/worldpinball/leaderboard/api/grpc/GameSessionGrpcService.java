package com.worldpinball.leaderboard.api.grpc;

import com.worldpinball.leaderboard.application.features.gamesessions.Public.addplayer.AddPlayerHandler;
import com.worldpinball.leaderboard.application.features.gamesessions.Public.addplayer.AddPlayerResult;
import com.worldpinball.leaderboard.application.features.gamesessions.Public.addplayer.commands.AddPlayerCommand;
import com.worldpinball.leaderboard.application.features.gamesessions.Public.getlobby.GetLobbyHandler;
import com.worldpinball.leaderboard.application.features.gamesessions.Public.getlobby.GetLobbyResult;
import com.worldpinball.leaderboard.application.features.gamesessions.Public.getlobby.dtos.LobbySlotDto;
import com.worldpinball.leaderboard.application.features.gamesessions.Public.getlobby.dtos.SessionLobbyDto;
import com.worldpinball.leaderboard.application.features.gamesessions.Public.getlobby.queries.GetLobbyQuery;
import com.worldpinball.leaderboard.application.features.gamesessions.Public.kickplayer.KickPlayerHandler;
import com.worldpinball.leaderboard.application.features.gamesessions.Public.kickplayer.KickPlayerResult;
import com.worldpinball.leaderboard.application.features.gamesessions.Public.kickplayer.commands.KickPlayerCommand;
import com.worldpinball.leaderboard.application.features.gamesessions.Public.launchsession.LaunchSessionHandler;
import com.worldpinball.leaderboard.application.features.gamesessions.Public.launchsession.LaunchSessionResult;
import com.worldpinball.leaderboard.application.features.gamesessions.Public.launchsession.commands.LaunchSessionCommand;
import com.worldpinball.leaderboard.application.features.gamesessions.Public.recordballscore.RecordBallScoreHandler;
import com.worldpinball.leaderboard.application.features.gamesessions.Public.recordballscore.RecordBallScoreResult;
import com.worldpinball.leaderboard.application.features.gamesessions.Public.recordballscore.commands.RecordBallScoreCommand;
import com.worldpinball.leaderboard.application.services.LiveTelemetryService;
import com.worldpinball.leaderboard.application.services.LobbyNotificationService;
import com.worldpinball.leaderboard.grpc.v1.AddPlayerRequest;
import com.worldpinball.leaderboard.grpc.v1.AddPlayerResponse;
import com.worldpinball.leaderboard.grpc.v1.GameSessionServiceGrpc;
import com.worldpinball.leaderboard.grpc.v1.GetLobbyRequest;
import com.worldpinball.leaderboard.grpc.v1.KickPlayerRequest;
import com.worldpinball.leaderboard.grpc.v1.KickPlayerResponse;
import com.worldpinball.leaderboard.grpc.v1.LaunchSessionRequest;
import com.worldpinball.leaderboard.grpc.v1.LaunchSessionResponse;
import com.worldpinball.leaderboard.grpc.v1.PlayerSlotInfo;
import com.worldpinball.leaderboard.grpc.v1.RecordBallScoreRequest;
import com.worldpinball.leaderboard.grpc.v1.RecordBallScoreResponse;
import com.worldpinball.leaderboard.grpc.v1.ScoreDeltaRequest;
import com.worldpinball.leaderboard.grpc.v1.SessionLobbyResponse;
import com.worldpinball.leaderboard.grpc.v1.StreamingSummaryResponse;
import com.worldpinball.leaderboard.grpc.v1.WatchAllLobbiesRequest;
import com.worldpinball.leaderboard.grpc.v1.WatchLobbyRequest;
import com.worldpinball.leaderboard.infrastructure.GameSessionRepository;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class GameSessionGrpcService extends GameSessionServiceGrpc.GameSessionServiceImplBase {

    private static final Set<String> TERMINAL_STATES = Set.of(
        "STARTED", "INGAME", "ENDED", "CLOSED", "CANCELLED", "ABANDONED", "ABORTED"
    );

    private final AddPlayerHandler addPlayerHandler;
    private final KickPlayerHandler kickPlayerHandler;
    private final GetLobbyHandler getLobbyHandler;
    private final RecordBallScoreHandler recordBallScoreHandler;
    private final LaunchSessionHandler launchSessionHandler;
    private final LobbyNotificationService lobbyNotifier;
    private final LiveTelemetryService liveTelemetryService;
    private final GameSessionRepository gameSessionRepository;

    @Override
    public void addPlayer(AddPlayerRequest request, StreamObserver<AddPlayerResponse> responseObserver) {
        UUID sessionId;
        try {
            sessionId = UUID.fromString(request.getSessionId());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid session ID format").asRuntimeException());
            return;
        }

        UUID userId = null;
        if (request.hasUserId() && !request.getUserId().isBlank()) {
            try {
                userId = UUID.fromString(request.getUserId());
            } catch (IllegalArgumentException e) {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid user ID format").asRuntimeException());
                return;
            }
        }

        String initials = request.hasGuestInitials() ? request.getGuestInitials() : null;
        var command = new AddPlayerCommand(sessionId, request.getSlotNumber(), userId, initials, false);
        AddPlayerResult result = addPlayerHandler.handle(command);

        if (result instanceof AddPlayerResult.Success s) {
            lobbyNotifier.notifyLobbyUpdated(sessionId);
            log.info("Added player to slot {} for session {}.", command.slotNumber(), command.sessionId());
            responseObserver.onNext(AddPlayerResponse.newBuilder()
                .setSuccess(true)
                .setSlotNumber(s.slotNumber())
                .setMessage("Player added.")
                .build());
            responseObserver.onCompleted();
        } else if (result instanceof AddPlayerResult.SessionNotFound) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("Session not found.").asRuntimeException());
        } else if (result instanceof AddPlayerResult.SessionNotJoinable) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription("Session is not joinable.").asRuntimeException());
        } else if (result instanceof AddPlayerResult.SlotAlreadyOccupied) {
            responseObserver.onError(Status.ALREADY_EXISTS.withDescription("Slot is already occupied.").asRuntimeException());
        } else if (result instanceof AddPlayerResult.InvalidSlot) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Slot number must be between 1 and 4.").asRuntimeException());
        } else if (result instanceof AddPlayerResult.RegisteredUserRequired) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Registered user required.").asRuntimeException());
        } else if (result instanceof AddPlayerResult.UserAlreadyRegistered) {
            responseObserver.onError(Status.ALREADY_EXISTS.withDescription("User already registered.").asRuntimeException());
        } else {
            responseObserver.onError(Status.INTERNAL.withDescription("Unexpected error.").asRuntimeException());
        }
    }

    @Override
    public void kickPlayer(KickPlayerRequest request, StreamObserver<KickPlayerResponse> responseObserver) {
        UUID sessionId;
        try {
            sessionId = UUID.fromString(request.getSessionId());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid session ID format").asRuntimeException());
            return;
        }

        var command = new KickPlayerCommand(sessionId, request.getSlotNumber());
        KickPlayerResult result = kickPlayerHandler.handle(command);

        if (result instanceof KickPlayerResult.Success s) {
            lobbyNotifier.notifyLobbyUpdated(sessionId);
            responseObserver.onNext(KickPlayerResponse.newBuilder()
                .setSuccess(true)
                .setSlotNumber(s.slotNumber())
                .setMessage("Player kicked.")
                .build());
            responseObserver.onCompleted();
        } else if (result instanceof KickPlayerResult.SessionNotFound) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("Session not found.").asRuntimeException());
        } else if (result instanceof KickPlayerResult.CannotRemovePlayerActiveSession) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription("Cannot remove player once session has started or ended.").asRuntimeException());
        } else if (result instanceof KickPlayerResult.PlayerNotFound) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("Player not found in the specified slot.").asRuntimeException());
        } else if (result instanceof KickPlayerResult.InvalidSlot) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Slot number must be between 1 and 4.").asRuntimeException());
        } else {
            responseObserver.onError(Status.INTERNAL.withDescription("Unexpected error.").asRuntimeException());
        }
    }

    @Override
    public void launchSession(LaunchSessionRequest request, StreamObserver<LaunchSessionResponse> responseObserver) {
        UUID sessionId;
        try {
            sessionId = UUID.fromString(request.getSessionId());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid session ID format").asRuntimeException());
            return;
        }

        var command = new LaunchSessionCommand(sessionId);
        LaunchSessionResult result = launchSessionHandler.handle(command);

        if (result instanceof LaunchSessionResult.Success(UUID id)) {
            lobbyNotifier.notifyLobbyUpdated(sessionId);
            responseObserver.onNext(LaunchSessionResponse.newBuilder()
                .setSuccess(true)
                .setSessionId(id.toString())
                .setMessage("Session launched.")
                .build());
            responseObserver.onCompleted();
        } else if (result instanceof LaunchSessionResult.SessionNotFound) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("Session not found.").asRuntimeException());
        } else if (result instanceof LaunchSessionResult.CannotStartSession(String message)) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(message).asRuntimeException());
        } else {
            responseObserver.onError(Status.INTERNAL.withDescription("Unexpected error.").asRuntimeException());
        }
    }

    @Override
    public void getLobby(GetLobbyRequest request, StreamObserver<SessionLobbyResponse> responseObserver) {
        UUID sessionId;
        try {
            sessionId = UUID.fromString(request.getSessionId());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid session ID format").asRuntimeException());
            return;
        }

        var query = new GetLobbyQuery(sessionId);
        GetLobbyResult result = getLobbyHandler.handle(query);

        if (result instanceof GetLobbyResult.Success s) {
            responseObserver.onNext(mapToLobbyResponse(s.lobby()));
            responseObserver.onCompleted();
        } else if (result instanceof GetLobbyResult.SessionNotFound) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("Session not found.").asRuntimeException());
        } else {
            responseObserver.onError(Status.INTERNAL.withDescription("Unexpected error.").asRuntimeException());
        }
    }

    @Override
    public void watchLobby(WatchLobbyRequest request, StreamObserver<SessionLobbyResponse> responseObserver) {
        UUID sessionId;
        try {
            sessionId = UUID.fromString(request.getSessionId());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid session ID format").asRuntimeException());
            return;
        }

        // Send initial snapshot
        GetLobbyResult initialResult = getLobbyHandler.handle(new GetLobbyQuery(sessionId));
        if (!(initialResult instanceof GetLobbyResult.Success initialSuccess)) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("Session not found.").asRuntimeException());
            return;
        }

        responseObserver.onNext(mapToLobbyResponse(initialSuccess.lobby()));

        if (isTerminal(initialSuccess.lobby().sessionState())) {
            responseObserver.onCompleted();
            return;
        }

        ServerCallStreamObserver<SessionLobbyResponse> serverObserver = (ServerCallStreamObserver<SessionLobbyResponse>) responseObserver;
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Runnable> unsubscribeRef = new AtomicReference<>();

        Runnable unsub = lobbyNotifier.subscribe(sessionId, sid -> {
            if (completed.get()) {
                return;
            }
            GetLobbyResult update = getLobbyHandler.handle(new GetLobbyQuery(sid));
            if (update instanceof GetLobbyResult.Success success) {
                synchronized (responseObserver) {
                    if (!completed.get()) {
                        responseObserver.onNext(mapToLobbyResponse(success.lobby()));
                        if (isTerminal(success.lobby().sessionState())) {
                            completed.set(true);
                            Runnable r = unsubscribeRef.get();
                            if (r != null) {
                                r.run();
                            }
                            responseObserver.onCompleted();
                        }
                    }
                }
            }
        });

        unsubscribeRef.set(unsub);
        serverObserver.setOnCancelHandler(() -> {
            completed.set(true);
            Runnable r = unsubscribeRef.get();
            if (r != null) {
                r.run();
            }
        });
    }

    @Override
    public void watchAllLobbies(WatchAllLobbiesRequest request, StreamObserver<SessionLobbyResponse> responseObserver) {
        // 1. Initial snapshot: send all currently active sessions
        List<UUID> activeSessionIds = gameSessionRepository.findActiveSessionIds();
        for (UUID activeId : activeSessionIds) {
            GetLobbyResult initial = getLobbyHandler.handle(new GetLobbyQuery(activeId));
            if (initial instanceof GetLobbyResult.Success success) {
                responseObserver.onNext(mapToLobbyResponse(success.lobby()));
            }
        }

        // 2. Real-time stream
        ServerCallStreamObserver<SessionLobbyResponse> serverObserver = (ServerCallStreamObserver<SessionLobbyResponse>) responseObserver;
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Runnable> unsubscribeRef = new AtomicReference<>();

        Runnable unsub = lobbyNotifier.subscribeAll(sid -> {
            if (completed.get()) {
                return;
            }
            GetLobbyResult update = getLobbyHandler.handle(new GetLobbyQuery(sid));
            if (update instanceof GetLobbyResult.Success success) {
                synchronized (responseObserver) {
                    if (!completed.get()) {
                        responseObserver.onNext(mapToLobbyResponse(success.lobby()));
                    }
                }
            }
        });

        unsubscribeRef.set(unsub);
        serverObserver.setOnCancelHandler(() -> {
            completed.set(true);
            Runnable r = unsubscribeRef.get();
            if (r != null) {
                r.run();
            }
        });
    }

    @Override
    public void recordBallScore(RecordBallScoreRequest request, StreamObserver<RecordBallScoreResponse> responseObserver) {
        UUID sessionId;
        UUID deviceId;
        try {
            sessionId = UUID.fromString(request.getSessionId());
            deviceId = UUID.fromString(request.getDeviceId());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid SessionId or DeviceId format.").asRuntimeException());
            return;
        }

        var command = new RecordBallScoreCommand(
            sessionId,
            deviceId,
            request.getSlotNumber(),
            request.getBallNumber(),
            request.getScore()
        );

        RecordBallScoreResult result = recordBallScoreHandler.handle(command);

        if (result instanceof RecordBallScoreResult.Success s) {
            lobbyNotifier.notifyLobbyUpdated(sessionId);
            responseObserver.onNext(RecordBallScoreResponse.newBuilder()
                .setSuccess(true)
                .setSessionId(s.sessionId().toString())
                .setSlotNumber(s.slotNumber())
                .setBallNumber(s.ballNumber())
                .setScore(s.score())
                .setMessage("Ball score recorded.")
                .build());
            responseObserver.onCompleted();
        } else if (result instanceof RecordBallScoreResult.SessionNotFound) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("Session not found.").asRuntimeException());
        } else if (result instanceof RecordBallScoreResult.DeviceMismatch) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Device mismatch.").asRuntimeException());
        } else if (result instanceof RecordBallScoreResult.CannotRecordBallScore c) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(c.reason()).asRuntimeException());
        } else {
            responseObserver.onError(Status.INTERNAL.withDescription("Unexpected error.").asRuntimeException());
        }
    }

    @Override
    public StreamObserver<ScoreDeltaRequest> streamLiveTelemetry(StreamObserver<StreamingSummaryResponse> responseObserver) {
        return new StreamObserver<>() {
            private long totalScore = 0;
            private int eventsProcessed = 0;
            private UUID sessionId = null;
            private int slotNumber = 0;

            @Override
            public void onNext(ScoreDeltaRequest delta) {
                try {
                    sessionId = UUID.fromString(delta.getSessionId());
                } catch (IllegalArgumentException e) {
                    responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid SessionId format.").asRuntimeException());
                    return;
                }
                slotNumber = delta.getSlotNumber();
                totalScore = liveTelemetryService.ingestDelta(
                    sessionId,
                    delta.getSlotNumber(),
                    delta.getPointsDelta(),
                    delta.getTriggerEvent()
                );
                eventsProcessed++;
            }

            @Override
            public void onError(Throwable t) {
                log.warn("Telemetry stream error: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(StreamingSummaryResponse.newBuilder()
                    .setSuccess(true)
                    .setSessionId(sessionId != null ? sessionId.toString() : "")
                    .setSlotNumber(slotNumber)
                    .setTotalStreamedScore(totalScore)
                    .setTotalEventsProcessed(eventsProcessed)
                    .setMessage("Live telemetry stream completed successfully.")
                    .build());
                responseObserver.onCompleted();
            }
        };
    }

    private static SessionLobbyResponse mapToLobbyResponse(SessionLobbyDto lobby) {
        var responseBuilder = SessionLobbyResponse.newBuilder()
            .setSessionId(lobby.sessionId().toString())
            .setSessionState(lobby.sessionState());

        for (LobbySlotDto slot : lobby.slots()) {
            var slotBuilder = PlayerSlotInfo.newBuilder()
                .setSlotNumber(slot.slotNumber())
                .setIsOccupied(slot.isOccupied());

            if (slot.userId() != null) {
                slotBuilder.setUserId(slot.userId().toString());
            }
            if (slot.guestInitials() != null && !slot.guestInitials().isBlank()) {
                slotBuilder.setGuestInitials(slot.guestInitials());
            }
            responseBuilder.addSlots(slotBuilder.build());
        }

        return responseBuilder.build();
    }

    private static boolean isTerminal(String sessionState) {
        return sessionState != null && TERMINAL_STATES.contains(sessionState.toUpperCase());
    }
}
