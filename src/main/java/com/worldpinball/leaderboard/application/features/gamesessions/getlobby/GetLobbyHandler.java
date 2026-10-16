package com.worldpinball.leaderboard.application.features.gamesessions.getlobby;

import com.worldpinball.leaderboard.application.features.gamesessions.getlobby.queries.GetLobbyQuery;

public interface GetLobbyHandler {
    GetLobbyResult handle(GetLobbyQuery query);
}
