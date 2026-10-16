package com.worldpinball.leaderboard.application.features.gamesessions.addplayer.commands;

import java.util.UUID;

public record AddPlayerCommand(
    UUID sessionId,
    int slotNumber,
    UUID userId,
    String guestInitials,
    boolean requiresRegisteredUser
) {}
