package com.worldpinball.leaderboard.unit;

import com.worldpinball.leaderboard.application.security.ca.CertificateAuthorityService;
import com.worldpinball.leaderboard.application.security.ca.IssuedCertificate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificateAuthorityServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-10-19T08:00:00Z");

    private CertificateAuthorityService sut;
    private String devicePublicKeyPem;
    private KeyPair deviceKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        sut = new CertificateAuthorityService(fixedClock);

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"));
        deviceKeyPair = keyGen.generateKeyPair();

        String base64Pub = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(deviceKeyPair.getPublic().getEncoded());
        devicePublicKeyPem = "-----BEGIN PUBLIC KEY-----\n" + base64Pub + "\n-----END PUBLIC KEY-----";
    }

    @Test
    @DisplayName("issueDeviceCertificate generates valid X.509v3 leaf certificate signed by CA with expected extensions")
    void issueDeviceCertificate_generatesValidX509v3CertificateWithExtensions() throws Exception {
        UUID machineId = UUID.randomUUID();
        String deviceSerial = "PINBALL-TPM-0042";

        IssuedCertificate issued = sut.issueDeviceCertificate(machineId, deviceSerial, devicePublicKeyPem);

        assertThat(issued.certPem()).startsWith("-----BEGIN CERTIFICATE-----");
        assertThat(issued.certPem()).endsWith("-----END CERTIFICATE-----");
        assertThat(issued.serialNumber()).hasSize(32).matches("^[0-9a-f]+$");
        assertThat(issued.machineId()).isEqualTo(machineId);
        assertThat(issued.subjectDn()).isEqualTo("CN=PINBALL-TPM-0042, O=WorldPinball, OU=Devices");
        assertThat(issued.issuerDn()).isEqualTo("CN=WorldPinball Intermediate CA, O=WorldPinball Internal CA");
        assertThat(issued.notBeforeUtc()).isEqualTo(FIXED_NOW.minus(5, ChronoUnit.MINUTES));
        assertThat(issued.notAfterUtc()).isEqualTo(FIXED_NOW.plus(365, ChronoUnit.DAYS));

        // Parse X.509 certificate from PEM and verify cryptographic signature with CA public key
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(issued.certPem().getBytes(StandardCharsets.UTF_8))
        );

        cert.verify(sut.getCaPublicKey());
        assertThat(cert.getPublicKey().getEncoded()).isEqualTo(deviceKeyPair.getPublic().getEncoded());

        // Verify SHA-256 thumbprint matches DER bytes
        String expectedThumbprint = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(cert.getEncoded())
        );
        assertThat(issued.thumbprintSha256()).isEqualTo(expectedThumbprint);

        // Verify BasicConstraints: leaf certificate (not a CA, getBasicConstraints() == -1)
        assertThat(cert.getBasicConstraints()).isEqualTo(-1);

        // Verify KeyUsage: DigitalSignature (index 0) and KeyAgreement (index 4)
        boolean[] keyUsage = cert.getKeyUsage();
        assertThat(keyUsage).isNotNull();
        assertThat(keyUsage[0]).isTrue(); // digitalSignature
        assertThat(keyUsage[4]).isTrue(); // keyAgreement

        // Verify ExtendedKeyUsage: TLS Web Client Authentication (1.3.6.1.5.5.7.3.2)
        assertThat(cert.getExtendedKeyUsage()).containsExactly("1.3.6.1.5.5.7.3.2");

        // Verify Subject Alternative Name (SAN): URI urn:pinball:machine:{machineId}
        Collection<List<?>> sans = cert.getSubjectAlternativeNames();
        assertThat(sans).isNotNull().hasSize(1);
        List<?> sanEntry = sans.iterator().next();
        assertThat(sanEntry.get(0)).isEqualTo(6); // GeneralName.uniformResourceIdentifier = 6
        assertThat(sanEntry.get(1)).isEqualTo("urn:pinball:machine:" + machineId);
    }

    @Test
    @DisplayName("issueDeviceCertificate rejects malformed public key PEM")
    void issueDeviceCertificate_whenPublicKeyPemMalformed_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> sut.issueDeviceCertificate(
                UUID.randomUUID(),
                "DEV-001",
                "-----BEGIN PUBLIC KEY-----\nNOT_A_VALID_KEY\n-----END PUBLIC KEY-----"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
