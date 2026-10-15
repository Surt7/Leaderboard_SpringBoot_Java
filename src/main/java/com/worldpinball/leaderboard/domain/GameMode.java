package com.worldpinball.leaderboard.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_modes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GameMode {

    @Id
    @Column(name = "mode_id", nullable = false)
    private UUID modeId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;
}
