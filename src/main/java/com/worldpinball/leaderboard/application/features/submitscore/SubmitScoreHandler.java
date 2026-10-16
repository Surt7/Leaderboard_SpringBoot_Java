package com.worldpinball.leaderboard.application.features.submitscore;

import com.worldpinball.leaderboard.application.features.submitscore.commands.SubmitScoreCommand;

public interface SubmitScoreHandler {
    SubmitScoreResult handle(SubmitScoreCommand command);
}
