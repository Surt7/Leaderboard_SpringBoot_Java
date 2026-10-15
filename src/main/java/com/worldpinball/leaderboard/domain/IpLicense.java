package com.worldpinball.leaderboard.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ip_licenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IpLicense {

    @Id
    @Column(name = "ip_id", nullable = false)
    private UUID ipId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "publisher_id")
    private UUID publisherId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;
}
