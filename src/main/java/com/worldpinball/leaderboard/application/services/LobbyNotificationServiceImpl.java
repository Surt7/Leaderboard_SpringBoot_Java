package com.worldpinball.leaderboard.application.services;

import com.worldpinball.leaderboard.common.EventId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
@Slf4j
public class LobbyNotificationServiceImpl implements LobbyNotificationService {

    private static final EventId LOBBY_UPDATED = new EventId(2901, "LobbyUpdated");
    private static final EventId SESSION_LAUNCHED = new EventId(2902, "SessionLaunched");
    private static final EventId SESSION_ABORTED = new EventId(2903, "SessionAborted");
    private static final EventId SCORE_SUBMITTED = new EventId(2904, "ScoreSubmitted");
    private static final EventId MATCH_FOUND = new EventId(2905, "MatchFound");

    private final ConcurrentHashMap<UUID, Set<Consumer<UUID>>> subscribers = new ConcurrentHashMap<>();
    private final Set<Consumer<UUID>> globalSubscribers = ConcurrentHashMap.newKeySet();

    @Override
    public void notifyLobbyUpdated(UUID sessionId) {
        log.info("{} Notification broadcast: Lobby updated for session {}.", LOBBY_UPDATED.tag(), sessionId);
        Set<Consumer<UUID>> sessionSubs = subscribers.get(sessionId);
        if (sessionSubs != null) {
            for (Consumer<UUID> sub : sessionSubs) {
                try {
                    sub.accept(sessionId);
                } catch (Exception e) {
                    log.warn("Error notifying subscriber for session {}: {}", sessionId, e.getMessage());
                }
            }
        }
        for (Consumer<UUID> sub : globalSubscribers) {
            try {
                sub.accept(sessionId);
            } catch (Exception e) {
                log.warn("Error notifying global subscriber for session {}: {}", sessionId, e.getMessage());
            }
        }
    }

    @Override
    public void notifySessionLaunched(UUID sessionId) {
        log.info("{} Notification broadcast: Session {} launched.", SESSION_LAUNCHED.tag(), sessionId);
        notifyLobbyUpdated(sessionId);
    }

    @Override
    public void notifySessionAborted(UUID sessionId) {
        log.info("{} Notification broadcast: Session {} aborted.", SESSION_ABORTED.tag(), sessionId);
        notifyLobbyUpdated(sessionId);
    }

    @Override
    public void notifyScoreSubmitted(UUID sessionId, int slotNumber, long score) {
        log.info("{} Notification broadcast: Score {} submitted for session {} slot {}.",
            SCORE_SUBMITTED.tag(), score, sessionId, slotNumber);
        notifyLobbyUpdated(sessionId);
    }

    @Override
    public void notifyMatchFound(UUID sessionId, UUID machineId) {
        log.info("{} Notification broadcast: Match found for session {} on machine {}.",
            MATCH_FOUND.tag(), sessionId, machineId);
        notifyLobbyUpdated(sessionId);
    }

    @Override
    public Runnable subscribe(UUID sessionId, Consumer<UUID> consumer) {
        subscribers.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(consumer);
        return () -> {
            Set<Consumer<UUID>> set = subscribers.get(sessionId);
            if (set != null) {
                set.remove(consumer);
                if (set.isEmpty()) {
                    subscribers.remove(sessionId);
                }
            }
        };
    }

    @Override
    public Runnable subscribeAll(Consumer<UUID> consumer) {
        globalSubscribers.add(consumer);
        return () -> globalSubscribers.remove(consumer);
    }
}
