package com.worldpinball.leaderboard.application.features.gamesessions.creategamesession;

import com.worldpinball.leaderboard.application.features.gamesessions.creategamesession.commands.CreateGameSessionCommand;

public interface CreateGameSessionHandler {
    CreateGameSessionResult handle(CreateGameSessionCommand command);
}
