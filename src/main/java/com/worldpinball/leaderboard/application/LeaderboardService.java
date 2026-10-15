package com.worldpinball.leaderboard.application;

import com.worldpinball.leaderboard.application.dto.LeaderboardEntryDto;
import com.worldpinball.leaderboard.application.dto.SubmitScoreRequest;
import com.worldpinball.leaderboard.domain.ScoreEntry;
import com.worldpinball.leaderboard.infrastructure.ScoreEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final ScoreEntryRepository scoreEntryRepository;

@Transactional
public ScoreEntry submitScore(SubmitScoreRequest request) {
        var existing = scoreEntryRepository.findBySessionIdAndSlotNumber(
                request.getSessionId(),
                request.getSlotNumber()
        );

        if (existing.isPresent()) {
            throw new IllegalStateException("A score for this session and slot already exists.");
        }

        Instant achievedAt = request.getAchievedAtUtc() != null ? request.getAchievedAtUtc() : Instant.now();
        ScoreEntry entry = new ScoreEntry(
                UUID.randomUUID(),
                request.getSlotNumber(),
                request.getSessionId(),
                request.getUserId(),
                request.getDeviceId(),
                request.getMachineId(),
                request.getIpId(),
                request.getModeId(),
                request.getScore(),
                achievedAt,
                true,
                null
        );

        return scoreEntryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDto> getTopScores(UUID ipId, UUID modeId, int limit) {

        var page = PageRequest.of(0, Math.min(Math.max(limit, 1), 100));
        var scores = scoreEntryRepository.findTopScoresByIpAndMode(ipId, modeId, page);
        List<LeaderboardEntryDto> result = new ArrayList<>();
        long currentRank = 1;
        for (var entry : scores) {
            result.add(LeaderboardEntryDto.builder()
                    .rank(currentRank++)
                    .scoreEntryId(entry.getScoreEntryId())
                    .score(entry.getScore())
                    .achievedAtUtc(entry.getAchievedAtUtc())
                    .slotNumber(entry.getSlotNumber())
                    .userId(entry.getUserId())
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public long getRank(UUID ipId, UUID modeId, long score) {
        return scoreEntryRepository.calculateRank(ipId, modeId, score);
    }


}
