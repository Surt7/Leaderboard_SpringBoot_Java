package com.worldpinball.leaderboard.application.features.gamesessions.Public.submitscore;

import com.worldpinball.leaderboard.application.features.gamesessions.Public.submitscore.commands.SubmitScoreCommand;
import com.worldpinball.leaderboard.common.EventId;
import com.worldpinball.leaderboard.domain.entities.ScoreEntry;
import com.worldpinball.leaderboard.infrastructure.ScoreEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmitScoreHandlerImpl implements SubmitScoreHandler {

    private static final EventId SCORE_ALREADY_EXISTS = new EventId(2401, "ScoreAlreadyExists");
    private static final EventId SCORE_SUBMITTED = new EventId(2402, "ScoreSubmitted");

    private final ScoreEntryRepository scoreEntryRepository;
    private final Clock clock;

    @Override
    @Transactional
    public SubmitScoreResult handle(SubmitScoreCommand command) {
        var existing = scoreEntryRepository.findBySessionIdAndSlotNumber(
            command.sessionId(),
            command.slotNumber()
        );

        if (existing.isPresent()) {
            log.warn("{} A score for session {} and slot {} already exists.",
                SCORE_ALREADY_EXISTS.tag(), command.sessionId(), command.slotNumber());
            return new SubmitScoreResult.SlotAlreadyOccupied(command.slotNumber());
        }

        Instant achievedAt = command.achievedAtUtc() != null ? command.achievedAtUtc() : Instant.now(clock);

        ScoreEntry entry = new ScoreEntry(
            command.sessionId(),
            command.slotNumber(),
            command.deviceId(),
            command.machineId(),
            command.ipId(),
            command.modeId(),
            command.score(),
            achievedAt,
            command.userId()
        );

        ScoreEntry saved = scoreEntryRepository.save(entry);

        log.info("{} Score {} recorded for session {} slot {}.",
            SCORE_SUBMITTED.tag(), saved.getScore(), saved.getSessionId(), saved.getSlotNumber());

        return new SubmitScoreResult.Success(List.of(saved.getScoreEntryId()));
    }
}
