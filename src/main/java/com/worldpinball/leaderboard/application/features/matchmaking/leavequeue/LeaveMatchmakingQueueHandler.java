package com.worldpinball.leaderboard.application.features.matchmaking.leavequeue;

public interface LeaveMatchmakingQueueHandler {
    LeaveMatchmakingQueueResult handle(LeaveMatchmakingQueueCommand command);
}
