package com.worldpinball.leaderboard.application.features.leaderboard.queries;

import com.worldpinball.leaderboard.application.features.leaderboard.dtos.LeaderboardRowDto;
import com.worldpinball.leaderboard.domain.entities.IpLicense;
import com.worldpinball.leaderboard.domain.entities.GameMode;
import com.worldpinball.leaderboard.domain.entities.ScoreEntry;
import com.worldpinball.leaderboard.domain.entities.UserProfile;
import com.worldpinball.leaderboard.infrastructure.GameModeRepository;
import com.worldpinball.leaderboard.infrastructure.IpLicenseRepository;
import com.worldpinball.leaderboard.infrastructure.ScoreEntryRepository;
import com.worldpinball.leaderboard.infrastructure.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetLeaderboardHandlerImpl implements GetLeaderboardHandler {

    private final ScoreEntryRepository scoreEntryRepository;
    private final IpLicenseRepository ipLicenseRepository;
    private final GameModeRepository gameModeRepository;
    private final UserProfileRepository userProfileRepository;

    @Override
    @Transactional(readOnly = true)
    public GetLeaderboardResult handle(GetLeaderboardQuery query) {
        UUID ipId = query.ipId();
        UUID modeId = query.modeId();

        // Resolve by slugs if GUIDs are not provided
        if (ipId == null && query.ipSlug() != null && !query.ipSlug().isBlank()) {
            Optional<IpLicense> ipOpt = ipLicenseRepository.findAll().stream()
                .filter(i -> query.ipSlug().equalsIgnoreCase(i.getSlug()))
                .findFirst();
            if (ipOpt.isPresent()) {
                ipId = ipOpt.get().getIpId();
            }
        }

        if (modeId == null && query.modeCode() != null && !query.modeCode().isBlank()) {
            Optional<GameMode> modeOpt = gameModeRepository.findAll().stream()
                .filter(m -> query.modeCode().equalsIgnoreCase(m.getCode()))
                .findFirst();
            if (modeOpt.isPresent()) {
                modeId = modeOpt.get().getModeId();
            }
        }

        if (ipId == null || modeId == null) {
            return new GetLeaderboardResult.InvalidParameters(
                "Either (ipId and modeId) or valid (ipSlug and modeCode) must be provided."
            );
        }

        String ipName = ipLicenseRepository.findById(ipId)
            .map(IpLicense::getName)
            .orElse("Unknown IP");

        String modeName = gameModeRepository.findById(modeId)
            .map(GameMode::getName)
            .orElse("Unknown Mode");

        var page = PageRequest.of(0, query.limit());
        List<ScoreEntry> scores = scoreEntryRepository.findTopScoresByIpAndMode(ipId, modeId, page);

        List<LeaderboardRowDto> rows = new ArrayList<>();
        long currentRank = 1;

        for (ScoreEntry score : scores) {
            String playerName = "Player";
            String initials = "AAA";

            if (score.getUserId() != null) {
                Optional<UserProfile> profileOpt = userProfileRepository.findById(score.getUserId());
                if (profileOpt.isPresent()) {
                    playerName = profileOpt.get().getDisplayName();
                    initials = profileOpt.get().getInitials();
                }
            }

            rows.add(new LeaderboardRowDto(
                currentRank++,
                score.getScoreEntryId(),
                initials,
                playerName,
                score.getScore(),
                score.getAchievedAtUtc(),
                ipName,
                modeName
            ));
        }

        return new GetLeaderboardResult.Success(rows);
    }
}
