package com.worldpinball.leaderboard.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "display_name", nullable = false)
    @Builder.Default
    private String displayName = "No Name";

    @Column(name = "initials", length = 4, nullable = false)
    @Builder.Default
    private String initials = "AAA";

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "country_code", length = 10)
    private String countryCode;

    @Column(name = "is_profile_public", nullable = false)
    @Builder.Default
    private boolean profilePublic = false;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc;
}
