package com.worldpinball.leaderboard.application.features.gamesessions.getlobby.dtos;

import java.util.UUID;

public record LobbySlotDto(
    int slotNumber,
    boolean isOccupied,
    UUID userId,
    String guestInitials
) {}
