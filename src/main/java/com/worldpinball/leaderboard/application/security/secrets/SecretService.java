package com.worldpinball.leaderboard.application.security.secrets;

import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class SecretService {

    private final SecureRandom secureRandom = new SecureRandom();

    public SecretGenerationResult generate() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        String plainBase64 = Base64.getEncoder().encodeToString(bytes);
        String hashHex = sha256Hex(bytes);

        return new SecretGenerationResult(plainBase64, hashHex);
    }

    public Optional<String> tryHash(String plainBase64Secret) {
        if (plainBase64Secret == null || plainBase64Secret.isBlank()) {
            return Optional.empty();
        }

        try {
            byte[] bytes = Base64.getDecoder().decode(plainBase64Secret.trim());
            if (bytes.length != 32) {
                return Optional.empty();
            }
            return Optional.of(sha256Hex(bytes));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String sha256Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
