package com.worldpinball.leaderboard.application.features.gamesessions.abortsession;

import com.worldpinball.leaderboard.application.features.gamesessions.abortsession.commands.AbortSessionCommand;

public interface AbortSessionHandler {
    AbortSessionResult handle(AbortSessionCommand command);
}
