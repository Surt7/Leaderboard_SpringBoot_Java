package com.worldpinball.leaderboard.application.features.matchmaking.joinqueue;

public interface JoinMatchmakingQueueHandler {
    JoinMatchmakingQueueResult handle(JoinMatchmakingQueueCommand command);
}
