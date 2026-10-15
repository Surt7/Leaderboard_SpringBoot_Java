package com.worldpinball.leaderboard.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GameSession {

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

    @Column(name = "state", length = 20, nullable = false)
    private String state = "Started";

    @Column(name = "nonce", length = 64, nullable = false)
    private String nonce;

    @Column(name = "client_version", length = 50)
    private String clientVersion;
}
