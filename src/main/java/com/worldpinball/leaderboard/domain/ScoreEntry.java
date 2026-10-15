package com.worldpinball.leaderboard.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "score_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScoreEntry {

    @Id
    @Column(name = "score_entry_id", nullable = false)
    private UUID scoreEntryId;

    @Column(name = "slot_number", nullable = false)
    private int slotNumber;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "machine_id", nullable = false)
    private UUID machineId;

    @Column(name = "ip_id", nullable = false)
    private UUID ipId;

    @Column(name = "mode_id", nullable = false)
    private UUID modeId;

    @Column(name = "score", nullable = false)
    private long score;

    @Column(name = "achieved_at_utc", nullable = false)
    private Instant achievedAtUtc;

    @Column(name = "is_validated", nullable = false)
    private boolean validated = true;

    @Column(name = "validation_reason")
    private String validationReason;
}
