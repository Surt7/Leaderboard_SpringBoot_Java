package com.worldpinball.leaderboard.application.features.gamesessions.addplayer;

import com.worldpinball.leaderboard.application.features.gamesessions.addplayer.commands.AddPlayerCommand;

public interface AddPlayerHandler {
    AddPlayerResult handle(AddPlayerCommand command);
}
