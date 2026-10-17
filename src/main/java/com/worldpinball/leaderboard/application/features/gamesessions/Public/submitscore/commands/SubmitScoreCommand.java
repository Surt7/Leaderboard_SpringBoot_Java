package com.worldpinball.leaderboard.application.features.gamesessions.Public.submitscore.commands;

import java.time.Instant;
import java.util.UUID;
public record SubmitScoreCommand(
        UUID sessionId,
        int slotNumber,
        UUID deviceId,
        UUID machineId,
        UUID ipId,
        UUID modeId,
        long score,
        Instant achievedAtUtc,
        UUID userId
) {
}
