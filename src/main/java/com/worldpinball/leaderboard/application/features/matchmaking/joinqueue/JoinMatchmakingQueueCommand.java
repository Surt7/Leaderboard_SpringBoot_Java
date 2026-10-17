package com.worldpinball.leaderboard.application.features.matchmaking.joinqueue;

import java.util.UUID;

public record JoinMatchmakingQueueCommand(UUID machineId, UUID playerId) {}
