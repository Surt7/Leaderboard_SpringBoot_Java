package com.worldpinball.leaderboard.application.features.gamesessions.kickplayer.commands;

import java.util.UUID;

public record KickPlayerCommand(
    UUID sessionId,
    int slotNumber
) {}
