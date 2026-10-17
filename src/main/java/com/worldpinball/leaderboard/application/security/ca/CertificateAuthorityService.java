package com.worldpinball.leaderboard.application.security.ca;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class CertificateAuthorityService {

    public IssuedCertificate issueDeviceCertificate(
            UUID machineId,
            String deviceSerialNumber,
            String publicKeyPem) {
        String serialHex = generateSerialNumber(machineId, deviceSerialNumber);
        String payload = String.format(
                "Subject: CN=%s, OU=Machine-%s%nSerial: %s%nIssuedAt: %s%nPublicKey: %s",
                deviceSerialNumber,
                machineId,
                serialHex,
                Instant.now(),
                publicKeyPem != null ? publicKeyPem.trim() : ""
        );
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String certPem = "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----";
        return new IssuedCertificate(certPem, serialHex);
    }

    private String generateSerialNumber(UUID machineId, String deviceSerialNumber) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = machineId.toString() + ":" + deviceSerialNumber + ":" + Instant.now().toEpochMilli();
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString().replace("-", "").toUpperCase();
        }
    }
}
