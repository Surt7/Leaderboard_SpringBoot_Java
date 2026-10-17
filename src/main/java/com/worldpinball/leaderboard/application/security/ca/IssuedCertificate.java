package com.worldpinball.leaderboard.application.security.ca;

public record IssuedCertificate(
        String certPem,
        String serialNumber
) {
}
