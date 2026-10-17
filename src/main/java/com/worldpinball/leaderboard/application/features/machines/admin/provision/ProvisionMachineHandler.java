package com.worldpinball.leaderboard.application.features.machines.admin.provision;

import com.worldpinball.leaderboard.application.features.machines.admin.provision.commands.ProvisionMachineCommand;

public interface ProvisionMachineHandler {
    ProvisionMachineResult handle(ProvisionMachineCommand command);
}
