package com.aeroflow.hub.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class TotpService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;
    private static final int CODE_DIGITS = 6;
    private static final long STEP_SECONDS = 30;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        SECURE_RANDOM.nextBytes(secret);
        return encodeBase32(secret);
    }

    public OptionalLong matchingStep(String secret, String code, Instant now, long lastAcceptedStep) {
        if (code == null || !code.matches("[0-9]{6}")) {
            return OptionalLong.empty();
        }

        long currentStep = now.getEpochSecond() / STEP_SECONDS;
        long matchedStep = -1;
        byte[] suppliedCode = code.getBytes(StandardCharsets.US_ASCII);
        for (long step = currentStep - 1; step <= currentStep + 1; step++) {
            if (step <= lastAcceptedStep) {
                continue;
            }
            if (MessageDigest.isEqual(generateCode(decodeBase32(secret), step)
                    .getBytes(StandardCharsets.US_ASCII), suppliedCode)) {
                matchedStep = Math.max(matchedStep, step);
            }
        }
        return matchedStep < 0 ? OptionalLong.empty() : OptionalLong.of(matchedStep);
    }

    static String encodeBase32(byte[] input) {
        StringBuilder output = new StringBuilder((input.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsInBuffer = 0;
        for (byte value : input) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsInBuffer += 8;
            while (bitsInBuffer >= 5) {
                output.append(BASE32_ALPHABET.charAt((buffer >> (bitsInBuffer - 5)) & 0x1f));
                bitsInBuffer -= 5;
            }
        }
        if (bitsInBuffer > 0) {
            output.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsInBuffer)) & 0x1f));
        }
        return output.toString();
    }

    private static byte[] decodeBase32(String input) {
        String normalized = input.replace("=", "").toUpperCase(java.util.Locale.ROOT);
        byte[] result = new byte[normalized.length() * 5 / 8];
        int buffer = 0;
        int bitsInBuffer = 0;
        int outputIndex = 0;
        for (char character : normalized.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(character);
            if (value < 0) {
                throw new IllegalArgumentException("Invalid TOTP secret");
            }
            buffer = (buffer << 5) | value;
            bitsInBuffer += 5;
            if (bitsInBuffer >= 8) {
                result[outputIndex++] = (byte) ((buffer >> (bitsInBuffer - 8)) & 0xff);
                bitsInBuffer -= 8;
            }
        }
        return result;
    }

    static String generateCode(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return "%06d".formatted(otp);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA1 is unavailable", exception);
        }
    }
}
