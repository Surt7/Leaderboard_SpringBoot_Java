package com.worldpinball.leaderboard.application.features.machines.admin.reissuetoken;

import com.worldpinball.leaderboard.application.features.machines.admin.reissuetoken.commands.ReissueEnrollmentTokenCommand;

public interface ReissueEnrollmentTokenHandler {
    ReissueEnrollmentTokenResult handle(ReissueEnrollmentTokenCommand command);
}
