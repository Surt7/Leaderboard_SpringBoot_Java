package com.worldpinball.leaderboard.application.features.matchmaking.matchnextplayer;

import java.util.UUID;

public record MatchNextPlayerCommand(UUID sessionId, UUID machineId) {}
