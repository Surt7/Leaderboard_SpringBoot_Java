package com.worldpinball.leaderboard.application.features.matchmaking.matchnextplayer;

public interface MatchNextPlayerHandler {
    MatchNextPlayerResult handle(MatchNextPlayerCommand command);
}
