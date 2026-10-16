package com.worldpinball.leaderboard.domain.entities;

import com.worldpinball.leaderboard.domain.enums.GameSessionState;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSession {

    public static final class ErrorMessages {
        private ErrorMessages() {}
        public static final String MACHINE_ID = "MachineId cannot be empty.";
        public static final String DEVICE_ID = "DeviceId cannot be empty.";
        public static final String MODE_ID = "ModeId cannot be empty.";
        public static final String NONCE = "Nonce cannot be empty.";
        public static final String CLIENT_VERSION = "ClientVersion cannot be empty.";
        public static final String STARTED_AT_UTC = "StartedAtUtc must be a valid date.";
        public static final String END_SESSION = "Cannot end a session that is not in Started state.";
        public static final String EARLIER = "EndedAtUtc cannot be earlier than StartedAtUtc.";
        public static final String PAUSE_SESSION = "Can only pause an active session.";
        public static final String RESUME_SESSION = "Can only resume a paused session.";
        public static final String CLOSED_SESSION = "Session is already closed.";
        public static final String CANNOT_START_SESSION = "Can only start a session that is in Created state.";
    }

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "machine_id", nullable = false)
    private UUID machineId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "mode_id", nullable = false)
    private UUID modeId;

    @Column(name = "started_at_utc", nullable = false)
    private Instant startedAtUtc;

    @Column(name = "ended_at_utc")
    private Instant endedAtUtc;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 20, nullable = false)
    private GameSessionState state = GameSessionState.CREATED;

    @Column(name = "nonce", length = 64, nullable = false)
    private String nonce;

    @Column(name = "client_version", length = 50)
    private String clientVersion;

    public GameSession(
        UUID machineId,
        UUID deviceId,
        UUID modeId,
        String clientVersion,
        String nonce,
        Instant startedAtUtc,
        UUID sessionId
    ) {
        if (machineId == null) {
            throw new IllegalArgumentException(ErrorMessages.MACHINE_ID);
        }
        if (deviceId == null) {
            throw new IllegalArgumentException(ErrorMessages.DEVICE_ID);
        }
        if (modeId == null) {
            throw new IllegalArgumentException(ErrorMessages.MODE_ID);
        }
        if (clientVersion == null || clientVersion.isBlank()) {
            throw new IllegalArgumentException(ErrorMessages.CLIENT_VERSION);
        }
        if (nonce == null || nonce.isBlank()) {
            throw new IllegalArgumentException(ErrorMessages.NONCE);
        }

        this.sessionId = sessionId != null ? sessionId : UUID.randomUUID();
        this.machineId = machineId;
        this.deviceId = deviceId;
        this.modeId = modeId;
        this.clientVersion = clientVersion.trim();
        this.nonce = nonce.trim();
        this.startedAtUtc = startedAtUtc != null ? startedAtUtc : Instant.now();
        this.state = GameSessionState.STARTED;
    }

    public void start(Instant now) {
        if (this.state != GameSessionState.CREATED) {
            throw new IllegalStateException(ErrorMessages.CANNOT_START_SESSION);
        }
        this.state = GameSessionState.STARTED;
        this.startedAtUtc = now != null ? now : Instant.now();
    }

    public void pause() {
        if (this.state != GameSessionState.STARTED) {
            throw new IllegalStateException(ErrorMessages.PAUSE_SESSION);
        }
        this.state = GameSessionState.PAUSED;
    }

    public void resume() {
        if (this.state != GameSessionState.PAUSED) {
            throw new IllegalStateException(ErrorMessages.RESUME_SESSION);
        }
        this.state = GameSessionState.STARTED;
    }

    public void endSession(Instant endedAtUtc) {
        if (this.state != GameSessionState.STARTED) {
            throw new IllegalStateException(ErrorMessages.END_SESSION);
        }
        Instant end = endedAtUtc != null ? endedAtUtc : Instant.now();
        if (end.isBefore(this.startedAtUtc)) {
            throw new IllegalArgumentException(ErrorMessages.EARLIER);
        }
        this.state = GameSessionState.ENDED;
        this.endedAtUtc = end;
    }

    public void cancel() {
        if (this.state == GameSessionState.ENDED || this.state == GameSessionState.CANCELLED) {
            throw new IllegalStateException(ErrorMessages.CLOSED_SESSION);
        }
        this.state = GameSessionState.CANCELLED;
        this.endedAtUtc = Instant.now();
    }
}
