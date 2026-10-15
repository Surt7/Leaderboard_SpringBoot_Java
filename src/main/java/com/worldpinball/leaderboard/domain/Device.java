package com.worldpinball.leaderboard.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Device {

    @Id
    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "serial_physical", nullable = false)
    private String serialPhysical;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "Active";

    @Column(name = "last_counter", nullable = false)
    private long lastCounter = 0;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;
}
