package com.worldpinball.leaderboard.application.features.gamesessions.kickplayer;

import com.worldpinball.leaderboard.application.features.gamesessions.kickplayer.commands.KickPlayerCommand;

public interface KickPlayerHandler {
    KickPlayerResult handle(KickPlayerCommand command);
}
