package com.worldpinball.leaderboard.infrastructure.seed;

import com.worldpinball.leaderboard.application.services.RealtimeLeaderboardService;
import com.worldpinball.leaderboard.domain.entities.*;
import com.worldpinball.leaderboard.infrastructure.*;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Mirror of C#'s DevelopmentDataSeeder (using Bogus).
 * Populates the development database with realistic test entities using Datafaker.
 * Activated strictly on the 'dev' profile.
 */
@Component
@Profile("dev")
public class DevelopmentDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentDataSeeder.class);
    private static final Faker faker = new Faker(new Random(20260903));

    private final PublisherRepository publisherRepository;
    private final GameModeRepository gameModeRepository;
    private final IpLicenseRepository ipLicenseRepository;
    private final MachineRepository machineRepository;
    private final DeviceRepository deviceRepository;
    private final MachineDeviceRepository machineDeviceRepository;
    private final UserProfileRepository userProfileRepository;
    private final GameSessionRepository gameSessionRepository;
    private final ScoreEntryRepository scoreEntryRepository;
    private final RealtimeLeaderboardService realtimeLeaderboardService;

    public DevelopmentDataSeeder(
            PublisherRepository publisherRepository,
            GameModeRepository gameModeRepository,
            IpLicenseRepository ipLicenseRepository,
            MachineRepository machineRepository,
            DeviceRepository deviceRepository,
            MachineDeviceRepository machineDeviceRepository,
            UserProfileRepository userProfileRepository,
            GameSessionRepository gameSessionRepository,
            ScoreEntryRepository scoreEntryRepository,
            RealtimeLeaderboardService realtimeLeaderboardService
    ) {
        this.publisherRepository = publisherRepository;
        this.gameModeRepository = gameModeRepository;
        this.ipLicenseRepository = ipLicenseRepository;
        this.machineRepository = machineRepository;
        this.deviceRepository = deviceRepository;
        this.machineDeviceRepository = machineDeviceRepository;
        this.userProfileRepository = userProfileRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.scoreEntryRepository = scoreEntryRepository;
        this.realtimeLeaderboardService = realtimeLeaderboardService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    @Transactional
    public void seed() {
        if (publisherRepository.count() > 0) {
            log.info("Development database already seeded. Skipping seeder.");
            return;
        }

        log.info("Starting Development database seeding (Datafaker)...");
        Instant now = Instant.now();

        // 1. Publishers
        List<Publisher> publishers = List.of(
                new Publisher("Stern Pinball", "stern-pinball", now),
                new Publisher("Williams Bally", "williams-bally", now),
                new Publisher("Jersey Jack Pinball", "jersey-jack", now),
                new Publisher("Spooky Pinball", "spooky-pinball", now),
                new Publisher("Chicago Gaming", "chicago-gaming", now)
        );
        publisherRepository.saveAll(publishers);

        // 2. Game Modes
        List<GameMode> modes = List.of(
                new GameMode("mode-1", "Standard Mode", "Default game mode for test tools and devices", true, now, UUID.randomUUID()),
                new GameMode("classic", "Classic Mode", "Standard 3-ball play", true, now, UUID.randomUUID()),
                new GameMode("timed", "Timed Rush", "Score as many points in 2 minutes", true, now, UUID.randomUUID()),
                new GameMode("multiball", "Multiball Madness", "Continuous multiball frenzy", true, now, UUID.randomUUID()),
                new GameMode("wizard", "Wizard Challenge", "All wizard modes unlocked", true, now, UUID.randomUUID()),
                new GameMode("tournament", "Tournament Mode", "No extra balls, strict tilt rules", true, now, UUID.randomUUID())
        );
        gameModeRepository.saveAll(modes);

        // 3. IP Licenses
        String[] titles = {
                "Godzilla", "Jurassic Park", "Medieval Madness", "Attack from Mars",
                "Deadpool", "Iron Maiden", "Stranger Things", "Monster Bash",
                "The Mandalorian", "Foo Fighters"
        };
        List<IpLicense> licenses = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            Publisher pub = publishers.get(i % publishers.size());
            String slug = titles[i].toLowerCase().replaceAll("[^a-z0-9]", "-");
            licenses.add(new IpLicense(titles[i], slug, pub.getPublisherId(), true, now, UUID.randomUUID()));
        }
        ipLicenseRepository.saveAll(licenses);

        // 4. User Profiles (12 players)
        List<UserProfile> users = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            String name = faker.name().firstName() + " " + faker.name().lastName();
            String initials = faker.regexify("[A-Z]{3}");
            users.add(new UserProfile(
                    UUID.randomUUID(),
                    name,
                    initials,
                    "https://avatar.worldpinball.com/" + i + ".png",
                    "FR",
                    true,
                    now.minus(i * 10L, ChronoUnit.DAYS)
            ));
        }
        userProfileRepository.saveAll(users);

        // 5. Machines & Devices (15 machines)
        List<Machine> machines = new ArrayList<>();
        List<Device> devices = new ArrayList<>();
        List<MachineDevice> machineDevices = new ArrayList<>();

        for (int i = 1; i <= 15; i++) {
            IpLicense ip = licenses.get(i % licenses.size());
            String serial = String.format("WP-%05d", i);
            String displayName = ip.getName() + " Pro #" + i;

            Machine machine = new Machine(
                    ip.getIpId(),
                    serial,
                    displayName,
                    now.minus(i, ChronoUnit.DAYS),
                    UUID.randomUUID(),
                    faker.internet().slug(),
                    UUID.randomUUID()
            );
            machine.completeEnrollment(now);
            machines.add(machine);

            Device device = Device.createActive(
                    String.format("DEVICE-%05d", i),
                    "dummy-public-key-" + i,
                    "ecdsa-p256",
                    now.minus(i, ChronoUnit.DAYS),
                    UUID.randomUUID()
            );
            devices.add(device);

            machineDevices.add(new MachineDevice(machine.getMachineId(), device.getDeviceId(), now.minus(i, ChronoUnit.DAYS)));
        }
        machineRepository.saveAll(machines);
        deviceRepository.saveAll(devices);
        machineDeviceRepository.saveAll(machineDevices);

        // 6. Game Sessions & Scores (30 sessions & leaderboard scores)
        List<GameSession> sessions = new ArrayList<>();
        List<ScoreEntry> scores = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            Machine machine = machines.get(i % machines.size());
            Device device = devices.get(i % devices.size());
            GameMode mode = modes.get(i % modes.size());
            UserProfile user = users.get(i % users.size());

            Instant sessionStart = now.minus(faker.number().numberBetween(1, 60), ChronoUnit.DAYS);
            Instant sessionEnd = sessionStart.plus(faker.number().numberBetween(3, 35), ChronoUnit.MINUTES);

            GameSession session = new GameSession(
                    machine.getMachineId(),
                    device.getDeviceId(),
                    mode.getModeId(),
                    "1.0.0",
                    faker.internet().slug(),
                    sessionStart,
                    UUID.randomUUID()
            );
            session.addPlayer(1, user.getUserId(), user.getInitials(), false);
            session.launch(sessionStart);
            session.endSession(sessionEnd);
            sessions.add(session);

            long points = faker.number().numberBetween(500_000L, 50_000_000L);
            scores.add(new ScoreEntry(
                    session.getSessionId(),
                    1,
                    device.getDeviceId(),
                    machine.getMachineId(),
                    machine.getIpId(),
                    mode.getModeId(),
                    points,
                    sessionEnd,
                    user.getUserId()
            ));
        }
        gameSessionRepository.saveAll(sessions);
        List<ScoreEntry> savedScores = scoreEntryRepository.saveAll(scores);

        // Warm up Redis ZSET Leaderboard with seeded scores
        for (ScoreEntry s : savedScores) {
            try {
                realtimeLeaderboardService.addScore(s.getIpId(), s.getModeId(), s.getScoreEntryId(), s.getScore());
            } catch (Exception ex) {
                log.warn("Could not warm up Redis ZSET for seeded score {}: {}", s.getScoreEntryId(), ex.getMessage());
            }
        }

        log.info("Development database seeding completed successfully (15 machines, 12 players, 30 scores).");
    }
}
