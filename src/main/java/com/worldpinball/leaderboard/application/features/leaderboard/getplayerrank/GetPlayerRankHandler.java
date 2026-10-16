package com.worldpinball.leaderboard.application.features.leaderboard.getplayerrank;

public interface GetPlayerRankHandler {
    GetPlayerRankResult handle(GetPlayerRankQuery query);
}
