package com.worldpinball.leaderboard.unit;

import com.worldpinball.leaderboard.domain.entities.GameSession;
import com.worldpinball.leaderboard.domain.entities.Machine;
import com.worldpinball.leaderboard.domain.entities.OutboxMessage;
import com.worldpinball.leaderboard.domain.entities.ScoreEntry;
import com.worldpinball.leaderboard.domain.enums.GameSessionState;
import com.worldpinball.leaderboard.domain.enums.MachineStatus;
import com.worldpinball.leaderboard.domain.enums.TelemetryMode;
import com.worldpinball.leaderboard.domain.events.ScoreSubmittedDomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEntitiesUnitTest {

    // -------------------- Machine Tests (mirrors MachineTests.cs) --------------------
    @Nested
    @DisplayName("Machine Domain Entity")
    class MachineUnitTests {

        private static final Instant REGISTERED_AT = Instant.parse("2026-09-02T12:00:00Z");

        @Test
        void constructor_whenValidArguments_initializesInPendingEnrollment() {
            UUID ipId = UUID.randomUUID();
            UUID idempotencyKey = UUID.randomUUID();

            Machine machine = new Machine(
                    ipId,
                    "WP-K7M4-9X2P-R8HD",
                    "Godzilla — Salle principale",
                    REGISTERED_AT,
                    idempotencyKey,
                    "nonce-abc",
                    null
            );

            assertThat(machine.getMachineId()).isNotNull();
            assertThat(machine.getIpId()).isEqualTo(ipId);
            assertThat(machine.getWorldPinballSerialNumber()).isEqualTo("WP-K7M4-9X2P-R8HD");
            assertThat(machine.getDisplayName()).isEqualTo("Godzilla — Salle principale");
            assertThat(machine.getRegisteredAtUtc()).isEqualTo(REGISTERED_AT);
            assertThat(machine.getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(machine.getNonce()).isEqualTo("nonce-abc");
            assertThat(machine.getStatus()).isEqualTo(MachineStatus.PENDING_ENROLLMENT);
        }

        @Test
        void constructor_whenIpIdIsNull_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new Machine(
                    null, "WP-1", "Godzilla", REGISTERED_AT, UUID.randomUUID(), "nonce", null
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessage(Machine.ErrorMessages.EMPTY_IP_ID);
        }

        @Test
        void constructor_whenSerialNumberIsBlank_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new Machine(
                    UUID.randomUUID(), "   ", "Godzilla", REGISTERED_AT, UUID.randomUUID(), "nonce", null
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessage(Machine.ErrorMessages.EMPTY_SERIAL_NUMBER);
        }

        @Test
        void constructor_whenDisplayNameIsBlank_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new Machine(
                    UUID.randomUUID(), "WP-1", "", REGISTERED_AT, UUID.randomUUID(), "nonce", null
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessage(Machine.ErrorMessages.EMPTY_DISPLAY_NAME);
        }

        @Test
        void constructor_whenIdempotencyKeyIsNull_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new Machine(
                    UUID.randomUUID(), "WP-1", "Godzilla", REGISTERED_AT, null, "nonce", null
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessage(Machine.ErrorMessages.EMPTY_IDEMPOTENCY_KEY);
        }

        @Test
        void constructor_whenNonceIsBlank_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new Machine(
                    UUID.randomUUID(), "WP-1", "Godzilla", REGISTERED_AT, UUID.randomUUID(), "   ", null
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessage(Machine.ErrorMessages.EMPTY_NONCE);
        }

        @Test
        void activate_whenPendingEnrollment_throwsIllegalStateException() {
            Machine machine = new Machine(
                    UUID.randomUUID(), "WP-1", "Godzilla", REGISTERED_AT, UUID.randomUUID(), "nonce", null
            );

            assertThatThrownBy(() -> machine.activate(Instant.now()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(Machine.ErrorMessages.PENDING_ENROLLMENT_REQUIRES_COMPLETION);
        }

        @Test
        void activate_whenRevoked_throwsIllegalStateException() {
            Machine machine = new Machine(
                    UUID.randomUUID(), "WP-1", "Godzilla", REGISTERED_AT, UUID.randomUUID(), "nonce", null
            );
            machine.setStatus(MachineStatus.REVOKED, Instant.now());

            assertThatThrownBy(() -> machine.activate(Instant.now()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(Machine.ErrorMessages.REVOKED_MACHINE_CANNOT_BE_REACTIVATED);
        }

        @Test
        void completeEnrollment_transitionsToActive() {
            Machine machine = new Machine(
                    UUID.randomUUID(), "WP-1", "Godzilla", REGISTERED_AT, UUID.randomUUID(), "nonce", null
            );

            machine.completeEnrollment(Instant.now());

            assertThat(machine.getStatus()).isEqualTo(MachineStatus.ACTIVE);
        }
    }

    // -------------------- GameSession Tests (mirrors GameSessionTests.cs) --------------------
    @Nested
    @DisplayName("GameSession Domain Entity")
    class GameSessionUnitTests {

        private GameSession createSession(Instant startedAt) {
            return new GameSession(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "1.0.0",
                    "nonce-xyz",
                    startedAt,
                    null
            );
        }

        @Test
        void constructor_whenValid_initializesInCreatedStateAndBallCheckpointTelemetry() {
            Instant now = Instant.now();
            GameSession session = createSession(now);

            assertThat(session.getSessionId()).isNotNull();
            assertThat(session.getState()).isEqualTo(GameSessionState.CREATED);
            assertThat(session.getTelemetryMode()).isEqualTo(TelemetryMode.BALL_CHECKPOINT_ONLY);
            assertThat(session.getEndedAtUtc()).isNull();
        }

        @Test
        void addPlayer_andRemoveLastPlayer_transitionsBetweenCreatedAndJoined() {
            GameSession session = createSession(Instant.now());
            UUID userId = UUID.randomUUID();

            session.addPlayer(1, userId);
            assertThat(session.getState()).isEqualTo(GameSessionState.JOINED);
            assertThat(session.getPlayers()).hasSize(1);

            session.removePlayer(1);
            assertThat(session.getState()).isEqualTo(GameSessionState.CREATED);
            assertThat(session.getPlayers()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 5, -1})
        void addPlayer_whenSlotOutOfRange_throwsIllegalArgumentException(int invalidSlot) {
            GameSession session = createSession(Instant.now());
            assertThatThrownBy(() -> session.addPlayer(invalidSlot, UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(GameSession.ErrorMessages.INVALID_SLOT_NUMBER);
        }

        @Test
        void addPlayer_whenDuplicateSlotOrDuplicateUser_throwsIllegalStateException() {
            GameSession session = createSession(Instant.now());
            UUID userId = UUID.randomUUID();
            session.addPlayer(1, userId);

            assertThatThrownBy(() -> session.addPlayer(1, UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(GameSession.ErrorMessages.SLOT_ALREADY_OCCUPIED);

            assertThatThrownBy(() -> session.addPlayer(2, userId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(GameSession.ErrorMessages.USER_ALREADY_REGISTERED);
        }

        @Test
        void launch_pause_resume_andEndSession_followsValidLifecycle() {
            Instant startedAt = Instant.now();
            GameSession session = createSession(startedAt);
            session.addPlayer(1, UUID.randomUUID());

            session.launch(startedAt);
            assertThat(session.getState()).isEqualTo(GameSessionState.STARTED);

            session.pause();
            assertThat(session.getState()).isEqualTo(GameSessionState.PAUSED);

            session.resume();
            assertThat(session.getState()).isEqualTo(GameSessionState.RESUMED);

            Instant endedAt = startedAt.plus(Duration.ofMinutes(10));
            session.endSession(endedAt);
            assertThat(session.getState()).isEqualTo(GameSessionState.ENDED);
            assertThat(session.getEndedAtUtc()).isEqualTo(endedAt);
        }

        @Test
        void launch_whenNoPlayers_throwsIllegalStateException() {
            GameSession session = createSession(Instant.now());
            assertThatThrownBy(() -> session.launch(Instant.now()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(GameSession.ErrorMessages.AT_LEAST_ONE_PLAYER_REQUIRED);
        }

        @Test
        void endSession_whenEndedAtEarlierThanStartedAt_throwsIllegalArgumentException() {
            Instant startedAt = Instant.now();
            GameSession session = createSession(startedAt);
            session.addPlayer(1, UUID.randomUUID());
            session.launch(startedAt);

            assertThatThrownBy(() -> session.endSession(startedAt.minusSeconds(10)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(GameSession.ErrorMessages.EARLIER);
        }

        @Test
        void enableLiveStreaming_updatesTelemetryMode() {
            GameSession session = createSession(Instant.now());
            session.enableLiveStreaming();
            assertThat(session.getTelemetryMode()).isEqualTo(TelemetryMode.LIVE_STREAMING);
        }
    }

    // -------------------- ScoreEntry Tests (mirrors ScoreEntryTests.cs) --------------------
    @Nested
    @DisplayName("ScoreEntry Domain Entity")
    class ScoreEntryUnitTests {

        @Test
        void constructor_whenValid_initializesAndEmitsScoreSubmittedDomainEvent() {
            UUID sessionId = UUID.randomUUID();
            UUID deviceId = UUID.randomUUID();
            UUID machineId = UUID.randomUUID();
            UUID ipId = UUID.randomUUID();
            UUID modeId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Instant now = Instant.now();

            ScoreEntry entry = new ScoreEntry(
                    sessionId, 1, deviceId, machineId, ipId, modeId, 12_500_000L, now, userId
            );

            assertThat(entry.getScoreEntryId()).isNotNull();
            assertThat(entry.getScore()).isEqualTo(12_500_000L);
            assertThat(entry.isValidated()).isTrue();
            assertThat(entry.getDomainEvents()).hasSize(1);
            assertThat(entry.getDomainEvents().getFirst()).isInstanceOf(ScoreSubmittedDomainEvent.class);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 5, -1})
        void constructor_whenSlotNumberOutOfRange_throwsIllegalArgumentException(int invalidSlot) {
            assertThatThrownBy(() -> new ScoreEntry(
                    UUID.randomUUID(), invalidSlot, UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), 100_000L, Instant.now(), null
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessage(ScoreEntry.ErrorMessages.INVALID_SLOT_NUMBER);
        }

        @Test
        void constructor_whenScoreNegative_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new ScoreEntry(
                    UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), -1L, Instant.now(), null
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessage(ScoreEntry.ErrorMessages.INVALID_SCORE);
        }

        @Test
        void invalidate_marksInvalidatedAndStoresReason() {
            ScoreEntry entry = new ScoreEntry(
                    UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), 100_000L, Instant.now(), null
            );

            entry.invalidate("Tilt triggered abnormally.");

            assertThat(entry.isValidated()).isFalse();
            assertThat(entry.getValidationReason()).isEqualTo("Tilt triggered abnormally.");
        }
    }

    // -------------------- OutboxMessage Tests (mirrors OutboxMessageTests.cs) --------------------
    @Nested
    @DisplayName("OutboxMessage Domain Entity")
    class OutboxMessageUnitTests {

        @Test
        void markAsProcessed_andRecordFailure_updateStateCorrectly() {
            Instant occurredAt = Instant.now();
            OutboxMessage msg = new OutboxMessage(
                    UUID.randomUUID(),
                    "ScoreSubmittedDomainEvent",
                    "{\"score\":1000}",
                    occurredAt
            );

            msg.recordFailure("Broker timeout");
            assertThat(msg.getRetryCount()).isEqualTo(1);
            assertThat(msg.getError()).isEqualTo("Broker timeout");
            assertThat(msg.getProcessedAtUtc()).isNull();

            Instant processedAt = occurredAt.plusSeconds(2);
            msg.markAsProcessed(processedAt);
            assertThat(msg.getProcessedAtUtc()).isEqualTo(processedAt);
            assertThat(msg.getError()).isNull();
        }
    }
}
