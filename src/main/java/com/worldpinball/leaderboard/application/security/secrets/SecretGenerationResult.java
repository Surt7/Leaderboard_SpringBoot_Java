package com.worldpinball.leaderboard.application.security.secrets;

public record SecretGenerationResult(
        String plainBase64,
        String hashHex
) {}
