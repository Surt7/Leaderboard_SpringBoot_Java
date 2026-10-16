package com.worldpinball.leaderboard.application.features.leaderboard.queries;

public interface GetLeaderboardHandler {
    GetLeaderboardResult handle(GetLeaderboardQuery query);
}
