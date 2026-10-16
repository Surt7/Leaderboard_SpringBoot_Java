package com.worldpinball.leaderboard.application.features.gamesessions.launchsession;

import com.worldpinball.leaderboard.application.features.gamesessions.launchsession.commands.LaunchSessionCommand;

public interface LaunchSessionHandler {
    LaunchSessionResult handle(LaunchSessionCommand command);
}
