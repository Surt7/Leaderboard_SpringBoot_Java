package com.worldpinball.leaderboard.application.features.leaderboard.queries;

public interface GetPlayerRankHandler {
    GetPlayerRankResult handle(GetPlayerRankQuery query);
}
