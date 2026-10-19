package com.worldpinball.leaderboard.unit;

import com.worldpinball.leaderboard.application.security.secrets.SecretGenerationResult;
import com.worldpinball.leaderboard.application.security.secrets.SecretService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretServiceTest {

    private SecretService sut;

    @BeforeEach
    void setUp() {
        sut = new SecretService();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("tryHash when secret is null or whitespace returns empty")
    void tryHash_whenSecretIsNullOrWhiteSpace_returnsEmpty(String invalidSecret) {
        Optional<String> result = sut.tryHash(invalidSecret);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("tryHash when secret is not valid Base64 returns empty")
    void tryHash_whenSecretIsNotValidBase64_returnsEmpty() {
        String malformedSecret = "ThisIsNotValidBase64!@#$";

        Optional<String> result = sut.tryHash(malformedSecret);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("tryHash when secret is Base64 but not 32 bytes returns empty")
    void tryHash_whenSecretIsBase64ButNot32Bytes_returnsEmpty() {
        byte[] shortSecretBytes = new byte[16];
        new SecureRandom().nextBytes(shortSecretBytes);
        String shortSecretBase64 = Base64.getEncoder().encodeToString(shortSecretBytes);

        Optional<String> result = sut.tryHash(shortSecretBase64);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("generate returns valid 32-byte secret, matching SHA-256 hash, and AES-256-GCM encrypted payload")
    void generate_whenCalled_returnsValidSecretMatchingHashAndEncryptedPayload() throws Exception {
        SecretGenerationResult result = sut.generate();

        byte[] decodedBytes = Base64.getDecoder().decode(result.plainBase64());
        byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(decodedBytes);
        String expectedHashHex = HexFormat.of().formatHex(sha256);

        assertThat(decodedBytes).hasSize(32);
        assertThat(result.hashHex()).isEqualTo(expectedHashHex);
        assertThat(result.encrypted()).isNotBlank();
        assertThat(sut.unprotect(result.encrypted())).isEqualTo(result.plainBase64());
    }

    @Test
    @DisplayName("tryHash when secret matches generated secret returns present with same hash")
    void tryHash_whenSecretMatchesGeneratedSecret_returnsSameHash() {
        SecretGenerationResult generated = sut.generate();

        Optional<String> calculatedHash = sut.tryHash(generated.plainBase64());

        assertThat(calculatedHash).isPresent().contains(generated.hashHex());
    }

    @Test
    @DisplayName("unprotect rejects tampered AES-256-GCM ciphertext")
    void unprotect_whenCiphertextTampered_throwsIllegalArgumentException() {
        SecretGenerationResult generated = sut.generate();
        byte[] rawEncrypted = Base64.getDecoder().decode(generated.encrypted());
        rawEncrypted[rawEncrypted.length - 1] ^= 0x01; // Flip 1 bit in GCM auth tag
        String tamperedBase64 = Base64.getEncoder().encodeToString(rawEncrypted);

        assertThatThrownBy(() -> sut.unprotect(tamperedBase64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AES-256-GCM");
    }
}
