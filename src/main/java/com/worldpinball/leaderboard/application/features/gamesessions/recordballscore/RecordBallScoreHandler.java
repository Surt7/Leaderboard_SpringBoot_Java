package com.worldpinball.leaderboard.application.features.gamesessions.recordballscore;

import com.worldpinball.leaderboard.application.features.gamesessions.recordballscore.commands.RecordBallScoreCommand;

public interface RecordBallScoreHandler {
    RecordBallScoreResult handle(RecordBallScoreCommand command);
}
