package com.worldpinball.leaderboard.application.features.machines.admin.getallmachines;

import com.worldpinball.leaderboard.domain.enums.MachineStatus;

import java.time.Instant;
import java.util.UUID;

public record MachineRowDto(
        UUID machineId,
        String machineName,
        String displayName,
        MachineStatus machineStatus,
        String machineNotes,
        Instant registeredAtUtc,
        Instant lastUpdated,
        String serialNumber
) {}
