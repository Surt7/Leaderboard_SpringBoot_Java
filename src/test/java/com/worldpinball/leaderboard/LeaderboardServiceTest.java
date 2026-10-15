package com.worldpinball.leaderboard;

import com.worldpinball.leaderboard.application.LeaderboardService;
import com.worldpinball.leaderboard.application.dto.LeaderboardEntryDto;
import com.worldpinball.leaderboard.application.dto.SubmitScoreRequest;
import com.worldpinball.leaderboard.domain.*;
import com.worldpinball.leaderboard.infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class LeaderboardServiceTest {

    @Autowired
    private PublisherRepository publisherRepository;

    @Autowired
    private IpLicenseRepository ipLicenseRepository;

    @Autowired
    private GameModeRepository gameModeRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private LeaderboardService leaderboardService;

    private IpLicense ipLicense;
    private GameMode gameMode;
    private Device device;
    private Machine machine;
    private GameSession session;

    @BeforeEach
    void setUp() {
        Publisher publisher = publisherRepository.save(new Publisher(
            UUID.randomUUID(), "Stern Pinball", "stern-" + UUID.randomUUID(), Instant.now()
        ));

        ipLicense = ipLicenseRepository.save(new IpLicense(
            UUID.randomUUID(), "Godzilla", "godzilla-" + UUID.randomUUID(), publisher.getPublisherId(), true, Instant.now()
        ));

        gameMode = gameModeRepository.save(new GameMode(
            UUID.randomUUID(), "std-" + UUID.randomUUID(), "Standard 3-Ball", true, Instant.now()
        ));

        device = deviceRepository.save(new Device(
            UUID.randomUUID(), "DEV-" + UUID.randomUUID(), "ACTIVE", 0, Instant.now()
        ));

        machine = machineRepository.save(new Machine(
            UUID.randomUUID(), ipLicense.getIpId(), "FLP-" + UUID.randomUUID().toString().substring(0, 8), "Godzilla Machine", "ACTIVE", UUID.randomUUID(), "nonce-" + UUID.randomUUID(), Instant.now()
        ));

        session = gameSessionRepository.save(new GameSession(
            UUID.randomUUID(), machine.getMachineId(), device.getDeviceId(), gameMode.getModeId(), Instant.now(), null, "STARTED", "nonce-" + UUID.randomUUID(), "1.0.0"
        ));
    }

    @Test
    @DisplayName("Should submit scores, prevent slot duplicates, and calculate accurate ranking")
    void testLeaderboardWorkflow() {
        // 1. Soumettre un premier score (250M) sur le slot 1
        SubmitScoreRequest score1 = SubmitScoreRequest.builder()
            .sessionId(session.getSessionId())
            .slotNumber(1)
            .deviceId(device.getDeviceId())
            .machineId(machine.getMachineId())
            .ipId(ipLicense.getIpId())
            .modeId(gameMode.getModeId())
            .score(250_000_000L)
            .achievedAtUtc(Instant.now().minusSeconds(10))
            .build();
        leaderboardService.submitScore(score1);

        // 2. Tenter de soumettre à nouveau sur le slot 1 (doit échouer)
        assertThatThrownBy(() -> leaderboardService.submitScore(score1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already exists");

        // 3. Soumettre un score supérieur (500M) sur le slot 2
        SubmitScoreRequest score2 = SubmitScoreRequest.builder()
            .sessionId(session.getSessionId())
            .slotNumber(2)
            .deviceId(device.getDeviceId())
            .machineId(machine.getMachineId())
            .ipId(ipLicense.getIpId())
            .modeId(gameMode.getModeId())
            .score(500_000_000L)
            .achievedAtUtc(Instant.now())
            .build();
        leaderboardService.submitScore(score2);

        // 4. Vérifier le Top 10 (le score de 500M doit être rang 1, 250M rang 2)
        List<LeaderboardEntryDto> topScores = leaderboardService.getTopScores(ipLicense.getIpId(), gameMode.getModeId(), 10);
        assertThat(topScores).hasSize(2);
        assertThat(topScores.get(0).getRank()).isEqualTo(1);
        assertThat(topScores.get(0).getScore()).isEqualTo(500_000_000L);
        assertThat(topScores.get(1).getRank()).isEqualTo(2);
        assertThat(topScores.get(1).getScore()).isEqualTo(250_000_000L);

        // 5. Vérifier le calcul du rang dynamique
        long rankFor300M = leaderboardService.getRank(ipLicense.getIpId(), gameMode.getModeId(), 300_000_000L);
        assertThat(rankFor300M).isEqualTo(2); // Seul 500M est supérieur, donc rang 2
    }
}
