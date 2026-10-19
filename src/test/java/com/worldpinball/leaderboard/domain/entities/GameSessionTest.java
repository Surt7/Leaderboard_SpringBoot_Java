package com.worldpinball.leaderboard.domain.entities;

import com.worldpinball.leaderboard.domain.enums.GameSessionState;
import com.worldpinball.leaderboard.domain.enums.TelemetryMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GameSession Domain Entity Unit Tests")
class GameSessionTest {

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
