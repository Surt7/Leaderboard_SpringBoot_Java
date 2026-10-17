package com.worldpinball.leaderboard.application.features.machines.admin.getallmachines;

import java.util.List;

public sealed interface GetAllMachinesResult {
    record Success(List<MachineRowDto> machines) implements GetAllMachinesResult {}
    record NotFound() implements GetAllMachinesResult {}
}
