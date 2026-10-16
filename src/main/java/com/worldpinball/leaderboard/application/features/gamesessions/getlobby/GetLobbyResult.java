package com.worldpinball.leaderboard.application.features.gamesessions.getlobby;

import com.worldpinball.leaderboard.application.features.gamesessions.getlobby.dtos.SessionLobbyDto;

import java.util.UUID;

public sealed interface GetLobbyResult {
    record Success(SessionLobbyDto lobby) implements GetLobbyResult {}
    record SessionNotFound(UUID sessionId) implements GetLobbyResult {}
}
