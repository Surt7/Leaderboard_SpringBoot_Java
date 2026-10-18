package com.worldpinball.leaderboard.unit;

import com.worldpinball.leaderboard.application.features.roles.Admin.grantpermission.GrantPermissionToRoleCommand;
import com.worldpinball.leaderboard.application.features.roles.Admin.grantpermission.GrantPermissionToRoleHandlerImpl;
import com.worldpinball.leaderboard.application.features.roles.Admin.grantpermission.GrantPermissionToRoleResult;
import com.worldpinball.leaderboard.application.features.roles.Admin.revokepermission.RevokePermissionFromRoleCommand;
import com.worldpinball.leaderboard.application.features.roles.Admin.revokepermission.RevokePermissionFromRoleHandlerImpl;
import com.worldpinball.leaderboard.application.features.roles.Admin.revokepermission.RevokePermissionFromRoleResult;
import com.worldpinball.leaderboard.application.services.EventPublisher;
import com.worldpinball.leaderboard.application.services.RolePermissionReloader;
import com.worldpinball.leaderboard.domain.entities.OutboxMessage;
import com.worldpinball.leaderboard.infrastructure.OutboxMessageRepository;
import com.worldpinball.leaderboard.infrastructure.RolePermissionRepository;
import com.worldpinball.leaderboard.infrastructure.outbox.OutboxProcessor;
import com.worldpinball.leaderboard.infrastructure.security.authorization.DynamicRolePermissionStore;
import com.worldpinball.leaderboard.infrastructure.security.authorization.FrozenRolePermissionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RolePermissionAndSecurityUnitTest {

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private RolePermissionReloader rolePermissionReloader;

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Test
    @DisplayName("FrozenRolePermissionStore matches role and permission case-insensitively")
    void frozenRolePermissionStore_matchesCaseInsensitively() {
        FrozenRolePermissionStore sut = new FrozenRolePermissionStore(Map.of(
                "Technician", List.of("MachinesProvision", "ShipmentsDispatch")
        ));

        assertThat(sut.hasPermission("Technician", "MachinesProvision")).isTrue();
        assertThat(sut.hasPermission("technician", "machinesprovision")).isTrue();
        assertThat(sut.hasPermission("Technician", "UnknownPerm")).isFalse();
        assertThat(sut.hasPermission("UnknownRole", "MachinesProvision")).isFalse();
        assertThat(sut.hasPermission(List.of("Player", "Technician"), "MachinesProvision")).isTrue();
    }

    @Test
    @DisplayName("DynamicRolePermissionStore reloads permissions atomically")
    void dynamicRolePermissionStore_reloadsAtomically() {
        DynamicRolePermissionStore sut = new DynamicRolePermissionStore(Map.of(
                "Technician", List.of("MachinesProvision")
        ));

        assertThat(sut.hasPermission("Technician", "ShipmentsDispatch")).isFalse();

        sut.reload(Map.of(
                "Technician", List.of("MachinesProvision", "ShipmentsDispatch")
        ));

        assertThat(sut.hasPermission("Technician", "ShipmentsDispatch")).isTrue();
    }

    @Test
    @DisplayName("GrantPermissionToRoleHandler grants new permission and reloads store")
    void grantPermissionToRoleHandler_grantsAndReloads() {
        when(rolePermissionRepository.existsByRoleAndPermission("Technician", "MachinesProvision"))
                .thenReturn(false);

        GrantPermissionToRoleHandlerImpl handler = new GrantPermissionToRoleHandlerImpl(
                rolePermissionRepository, rolePermissionReloader
        );

        GrantPermissionToRoleResult result = handler.handle(
                new GrantPermissionToRoleCommand("Technician", "MachinesProvision")
        );

        assertThat(result).isInstanceOf(GrantPermissionToRoleResult.Success.class);
        verify(rolePermissionRepository).save(any());
        verify(rolePermissionReloader).reloadFromDatabase();
    }

    @Test
    @DisplayName("RevokePermissionFromRoleHandler revokes existing permission and reloads store")
    void revokePermissionFromRoleHandler_revokesAndReloads() {
        when(rolePermissionRepository.existsByRoleAndPermission("Technician", "MachinesProvision"))
                .thenReturn(true);

        RevokePermissionFromRoleHandlerImpl handler = new RevokePermissionFromRoleHandlerImpl(
                rolePermissionRepository, rolePermissionReloader
        );

        RevokePermissionFromRoleResult result = handler.handle(
                new RevokePermissionFromRoleCommand("Technician", "MachinesProvision")
        );

        assertThat(result).isInstanceOf(RevokePermissionFromRoleResult.Success.class);
        verify(rolePermissionRepository).deleteByRoleAndPermission("Technician", "MachinesProvision");
        verify(rolePermissionReloader).reloadFromDatabase();
    }

    @Test
    @DisplayName("OutboxProcessor publishes unprocessed messages and records failures on exception")
    void outboxProcessor_publishesAndHandlesFailures() {
        Instant fixedNow = Instant.parse("2026-10-18T08:00:00Z");
        Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

        OutboxMessage msgOk = new OutboxMessage(UUID.randomUUID(), "ScoreSubmitted", "{\"ok\":true}", fixedNow);
        OutboxMessage msgFail = new OutboxMessage(UUID.randomUUID(), "ScoreSubmitted", "{\"ok\":false}", fixedNow);

        when(outboxMessageRepository.findUnprocessedBatchForUpdate(20))
                .thenReturn(List.of(msgOk, msgFail));

        lenient().doThrow(new RuntimeException("Kafka broker unreachable"))
                .when(eventPublisher)
                .publish(eq("game-sessions"), eq(msgFail.getId().toString()), any());

        OutboxProcessor processor = new OutboxProcessor(outboxMessageRepository, eventPublisher, fixedClock);
        int processed = processor.processBatch();

        assertThat(processed).isEqualTo(2);
        assertThat(msgOk.getProcessedAtUtc()).isEqualTo(fixedNow);
        assertThat(msgOk.getError()).isNull();

        assertThat(msgFail.getProcessedAtUtc()).isNull();
        assertThat(msgFail.getRetryCount()).isEqualTo(1);
        assertThat(msgFail.getError()).contains("Kafka broker unreachable");
    }
}
