package com.worldpinball.leaderboard.unit;

import com.worldpinball.leaderboard.application.security.ca.CertificateAuthorityService;
import com.worldpinball.leaderboard.application.security.ca.IssuedCertificate;
import com.worldpinball.leaderboard.application.services.MachineStatusCacheService;
import com.worldpinball.leaderboard.domain.entities.GameSession;
import com.worldpinball.leaderboard.domain.entities.Machine;
import com.worldpinball.leaderboard.domain.entities.OperatorToken;
import com.worldpinball.leaderboard.infrastructure.MachineRepository;
import com.worldpinball.leaderboard.infrastructure.OperatorTokenRepository;
import com.worldpinball.leaderboard.infrastructure.security.authentication.ClaimsPrincipal;
import com.worldpinball.leaderboard.infrastructure.security.authentication.ClaimsPrincipal.Claim;
import com.worldpinball.leaderboard.infrastructure.security.authentication.MachineCertificateAuthenticator;
import com.worldpinball.leaderboard.infrastructure.security.authentication.MachineCertificateAuthenticator.CertificateAuthResult;
import com.worldpinball.leaderboard.infrastructure.security.authentication.MachineClaimsTransformation;
import com.worldpinball.leaderboard.infrastructure.security.authentication.OperatorTokenAuthenticationHandler;
import com.worldpinball.leaderboard.infrastructure.security.authentication.OperatorTokenAuthenticationHandler.AuthenticateResult;
import com.worldpinball.leaderboard.infrastructure.security.authorization.ActiveMachineAuthorizationHandler;
import com.worldpinball.leaderboard.infrastructure.security.authorization.DynamicRolePermissionStore;
import com.worldpinball.leaderboard.infrastructure.security.authorization.GameSessionParticipantAuthorizationHandler;
import com.worldpinball.leaderboard.infrastructure.security.authorization.OperatorScopeAuthorizationHandler;
import com.worldpinball.leaderboard.infrastructure.security.authorization.PermissionAuthorizationHandler;
import com.worldpinball.leaderboard.infrastructure.security.operatortokens.OperatorTokenValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationAndAuthorizationUnitTest {

    @Mock
    private MachineStatusCacheService machineStatusCache;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private OperatorTokenRepository operatorTokenRepository;

    // -------------------------------------------------------------------------
    // 1. MachineCertificateAuthenticator tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MachineCertificateAuthenticator extracts machineId from SAN URN and attaches Machine role")
    void machineCertificateAuthenticator_whenValidLeafCert_returnsSuccessPrincipal() throws Exception {
        UUID machineId = UUID.randomUUID();
        CertificateAuthorityService caService = new CertificateAuthorityService();

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = keyGen.generateKeyPair();
        String pubPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(kp.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";

        IssuedCertificate issued = caService.issueDeviceCertificate(machineId, "DEV-SN-99", pubPem);
        X509Certificate x509 = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(issued.certPem().getBytes(StandardCharsets.UTF_8)));

        MachineCertificateAuthenticator sut = new MachineCertificateAuthenticator();
        CertificateAuthResult result = sut.authenticate(x509);

        assertThat(result).isInstanceOf(CertificateAuthResult.Success.class);
        CertificateAuthResult.Success success = (CertificateAuthResult.Success) result;
        assertThat(success.machineId()).isEqualTo(machineId);
        assertThat(success.principal().findFirst(ClaimsPrincipal.CLAIM_NAME_IDENTIFIER))
                .contains(machineId.toString());
        assertThat(success.principal().isInRole("Machine")).isTrue();
    }

    // -------------------------------------------------------------------------
    // 2. MachineClaimsTransformation tests (mirroring C# MachineClaimsTransformationTests)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MachineClaimsTransformation when claim already exists returns without calling cache or DB")
    void transform_whenClaimAlreadyExists_returnsWithoutCallingCacheOrDb() {
        MachineClaimsTransformation sut = new MachineClaimsTransformation(machineStatusCache, machineRepository);
        ClaimsPrincipal principal = new ClaimsPrincipal("TestAuth", List.of(
                new Claim("machine_status", "Active")
        ));

        ClaimsPrincipal result = sut.transform(principal);

        assertThat(result.findFirst("machine_status")).contains("Active");
        verify(machineStatusCache, never()).getStatus(any());
        verify(machineRepository, never()).findById(any());
    }

    @Test
    @DisplayName("MachineClaimsTransformation when status is in Redis cache adds claim without calling DB")
    void transform_whenStatusInRedisCache_addsClaimWithoutCallingDb() {
        UUID machineId = UUID.randomUUID();
        when(machineStatusCache.getStatus(machineId)).thenReturn(Optional.of("Active"));

        MachineClaimsTransformation sut = new MachineClaimsTransformation(machineStatusCache, machineRepository);
        ClaimsPrincipal principal = new ClaimsPrincipal("TestAuth", List.of(
                new Claim(ClaimsPrincipal.CLAIM_NAME_IDENTIFIER, machineId.toString())
        ));

        ClaimsPrincipal result = sut.transform(principal);

        assertThat(result.findFirst("machine_status")).contains("Active");
        verify(machineRepository, never()).findById(any());
    }

    @Test
    @DisplayName("MachineClaimsTransformation when not in Redis fetches from DB and warms up Redis")
    void transform_whenNotInRedis_fetchesFromDbAndWarmsUpRedis() {
        UUID machineId = UUID.randomUUID();
        Instant now = Instant.now();
        Machine machine = new Machine(UUID.randomUUID(), "SN-12345", "Godzilla LE", now, UUID.randomUUID(), "nonce-1234567890123456", machineId);
        machine.completeEnrollment(now);

        when(machineStatusCache.getStatus(machineId)).thenReturn(Optional.empty());
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(machine));

        MachineClaimsTransformation sut = new MachineClaimsTransformation(machineStatusCache, machineRepository);
        ClaimsPrincipal principal = new ClaimsPrincipal("TestAuth", List.of(
                new Claim(ClaimsPrincipal.CLAIM_NAME_IDENTIFIER, machineId.toString())
        ));

        ClaimsPrincipal result = sut.transform(principal);

        assertThat(result.findFirst("machine_status")).contains("Active");
        verify(machineStatusCache, times(1)).setStatus(machineId, "Active", Duration.ofMinutes(5));
    }

    // -------------------------------------------------------------------------
    // 3. ActiveMachineAuthorizationHandler tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ActiveMachineAuthorizationHandler succeeds when machine_status is Active")
    void activeMachineHandler_whenMachineIsActive_succeeds() {
        ActiveMachineAuthorizationHandler handler = new ActiveMachineAuthorizationHandler();
        ClaimsPrincipal user = new ClaimsPrincipal("TestAuth", List.of(
                new Claim("machine_status", "Active")
        ));

        assertThat(handler.isAuthorized(user)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"Suspended", "Pending", "PendingEnrollment"})
    @DisplayName("ActiveMachineAuthorizationHandler fails when machine_status is not Active")
    void activeMachineHandler_whenMachineIsNotActive_fails(String status) {
        ActiveMachineAuthorizationHandler handler = new ActiveMachineAuthorizationHandler();
        List<Claim> claims = status != null ? List.of(new Claim("machine_status", status)) : List.of();
        ClaimsPrincipal user = new ClaimsPrincipal("TestAuth", claims);

        assertThat(handler.isAuthorized(user)).isFalse();
    }

    // -------------------------------------------------------------------------
    // 4. GameSessionParticipantAuthorizationHandler tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GameSessionParticipantAuthorizationHandler succeeds when user is participant")
    void gameSessionParticipantHandler_whenUserIsParticipant_succeeds() {
        UUID userId = UUID.randomUUID();
        ClaimsPrincipal user = new ClaimsPrincipal("TestAuth", List.of(
                new Claim(ClaimsPrincipal.CLAIM_NAME_IDENTIFIER, userId.toString())
        ));

        GameSession session = new GameSession(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "1.0", "nonce-1234567890", Instant.now(), UUID.randomUUID()
        );
        session.addPlayer(1, userId);

        GameSessionParticipantAuthorizationHandler sut = new GameSessionParticipantAuthorizationHandler();

        assertThat(sut.isAuthorized(user, session)).isTrue();
    }

    @Test
    @DisplayName("GameSessionParticipantAuthorizationHandler fails when user is not participant")
    void gameSessionParticipantHandler_whenUserIsNotParticipant_fails() {
        UUID strangerId = UUID.randomUUID();
        ClaimsPrincipal user = new ClaimsPrincipal("TestAuth", List.of(
                new Claim(ClaimsPrincipal.CLAIM_NAME_IDENTIFIER, strangerId.toString())
        ));

        GameSession session = new GameSession(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "1.0", "nonce-1234567890", Instant.now(), UUID.randomUUID()
        );
        session.addPlayer(1, UUID.randomUUID());

        GameSessionParticipantAuthorizationHandler sut = new GameSessionParticipantAuthorizationHandler();

        assertThat(sut.isAuthorized(user, session)).isFalse();
    }

    @Test
    @DisplayName("GameSessionParticipantAuthorizationHandler succeeds when caller is the hosting machine")
    void gameSessionParticipantHandler_whenCallerIsMachine_succeeds() {
        UUID machineId = UUID.randomUUID();
        ClaimsPrincipal user = new ClaimsPrincipal("TestAuth", List.of(
                new Claim(ClaimsPrincipal.CLAIM_NAME_IDENTIFIER, machineId.toString())
        ));

        GameSession session = new GameSession(
                machineId, UUID.randomUUID(), UUID.randomUUID(),
                "1.0", "nonce-1234567890", Instant.now(), UUID.randomUUID()
        );

        GameSessionParticipantAuthorizationHandler sut = new GameSessionParticipantAuthorizationHandler();

        assertThat(sut.isAuthorized(user, session)).isTrue();
    }

    // -------------------------------------------------------------------------
    // 5. PermissionAuthorizationHandler & OperatorToken tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PermissionAuthorizationHandler succeeds when user has role with permission and fails otherwise")
    void permissionAuthorizationHandler_evaluatesRoleAndAdminCorrectly() {
        DynamicRolePermissionStore store = new DynamicRolePermissionStore(Map.of(
                "Technician", List.of("MachinesProvision")
        ));
        PermissionAuthorizationHandler sut = new PermissionAuthorizationHandler(store);

        ClaimsPrincipal techUser = new ClaimsPrincipal("TestAuth", List.of(
                new Claim(ClaimsPrincipal.CLAIM_ROLE, "Technician")
        ));
        ClaimsPrincipal playerUser = new ClaimsPrincipal("TestAuth", List.of(
                new Claim(ClaimsPrincipal.CLAIM_ROLE, "Player")
        ));
        ClaimsPrincipal adminUser = new ClaimsPrincipal("TestAuth", List.of(
                new Claim(ClaimsPrincipal.CLAIM_ROLE, "Admin")
        ));

        assertThat(sut.isAuthorized(techUser, "MachinesProvision")).isTrue();
        assertThat(sut.isAuthorized(playerUser, "MachinesProvision")).isFalse();
        assertThat(sut.isAuthorized(adminUser, "AnyPermission")).isTrue();
    }

    @Test
    @DisplayName("OperatorTokenAuthenticationHandler authenticates valid token and OperatorScopeAuthorizationHandler checks scope")
    void operatorTokenAuthAndScopeHandler_validatesTokenAndScopes() {
        Instant fixedNow = Instant.parse("2026-10-19T08:00:00Z");
        Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        String rawToken = "op_live_1234567890abcdef12345678";

        OperatorToken token = new OperatorToken(
                "Factory Line #1",
                rawToken,
                "enroll, sessions",
                "admin-1",
                fixedNow.plus(Duration.ofDays(7)),
                fixedNow
        );

        when(operatorTokenRepository.findByTokenHash(OperatorToken.hashToken(rawToken)))
                .thenReturn(Optional.of(token));

        OperatorTokenValidator validator = new OperatorTokenValidator(operatorTokenRepository, fixedClock);
        OperatorTokenAuthenticationHandler authHandler = new OperatorTokenAuthenticationHandler(validator);
        OperatorScopeAuthorizationHandler scopeHandler = new OperatorScopeAuthorizationHandler();

        AuthenticateResult authResult = authHandler.authenticate(rawToken, null);
        assertThat(authResult).isInstanceOf(AuthenticateResult.Success.class);

        ClaimsPrincipal principal = ((AuthenticateResult.Success) authResult).principal();
        assertThat(scopeHandler.isAuthorized(principal, "enroll")).isTrue();
        assertThat(scopeHandler.isAuthorized(principal, "sessions")).isTrue();
        assertThat(scopeHandler.isAuthorized(principal, "unknown")).isFalse();

        // Second call hits in-memory L1 cache without querying DB again
        authHandler.authenticate(null, "Operator " + rawToken);
        verify(operatorTokenRepository, times(1)).findByTokenHash(any());
    }
}
