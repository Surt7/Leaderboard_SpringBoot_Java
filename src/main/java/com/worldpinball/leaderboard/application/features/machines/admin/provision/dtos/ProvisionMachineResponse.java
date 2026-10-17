package com.worldpinball.leaderboard.application.features.machines.admin.provision.dtos;

import java.util.UUID;

public record ProvisionMachineResponse(
        UUID machineId,
        String enrollmentToken
) {}
