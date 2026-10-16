package com.worldpinball.leaderboard.application.features.leaderboard.getleaderboard;

public interface GetLeaderboardHandler {
    GetLeaderboardResult handle(GetLeaderboardQuery query);
}
