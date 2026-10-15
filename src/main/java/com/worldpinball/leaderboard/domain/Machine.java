package com.worldpinball.leaderboard.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "machines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Machine {

    @Id
    @Column(name = "machine_id", nullable = false)
    private UUID machineId;

    @Column(name = "ip_id", nullable = false)
    private UUID ipId;

    @Column(name = "worldpinball_serial_number", length = 32, nullable = false)
    private String worldPinballSerialNumber;

    @Column(name = "display_name", length = 150, nullable = false)
    private String displayName;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "Active";

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "nonce", length = 64, nullable = false)
    private String nonce;

    @Column(name = "registered_at_utc", nullable = false)
    private Instant registeredAtUtc;
}
