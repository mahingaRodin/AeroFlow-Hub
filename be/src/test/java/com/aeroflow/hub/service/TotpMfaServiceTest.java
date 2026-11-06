package com.aeroflow.hub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aeroflow.hub.model.MfaLoginChallenge;
import com.aeroflow.hub.model.MfaRecoveryCode;
import com.aeroflow.hub.model.User;
import com.aeroflow.hub.repository.MfaLoginChallengeRepository;
import com.aeroflow.hub.repository.MfaRecoveryCodeRepository;
import com.aeroflow.hub.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class TotpMfaServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");
    private static final String CHALLENGE = "opaque-login-challenge";
    private static final String RECOVERY_CODE = "ABCDE-FGHIJ-KLMNO-PQRST-UVWX";

    @Mock
    private UserRepository userRepository;

    @Mock
    private MfaLoginChallengeRepository challengeRepository;

    @Mock
    private MfaRecoveryCodeRepository recoveryCodeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private TotpMfaService mfaService;
    private User user;
    private MfaLoginChallenge challenge;

    @BeforeEach
    void setUp() throws Exception {
        var cipher = new MfaSecretCipher(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        var totpService = new TotpService();
        mfaService = new TotpMfaService(
                userRepository,
                challengeRepository,
                recoveryCodeRepository,
                passwordEncoder,
                cipher,
                totpService,
                Clock.fixed(NOW, ZoneOffset.UTC));

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmailVerified(true);
        user.setEnabled(true);
        user.setMfaEnabled(true);
        user.setTotpSecretEncrypted(cipher.encrypt(
                TotpService.encodeBase32("12345678901234567890".getBytes(StandardCharsets.US_ASCII))));
        user.setTotpLastUsedStep(-1);
        challenge = new MfaLoginChallenge();
        challenge.setUser(user);
        challenge.setCreatedAt(NOW);
        challenge.setExpiresAt(NOW.plusSeconds(300));
        challenge.setFailedAttempts(0);
    }

    @Test
    void acceptsRecoveryCodeOnceAndConsumesBothCodeAndChallenge() throws Exception {
        challenge.setTokenHash(hash(CHALLENGE));
        MfaRecoveryCode recoveryCode = new MfaRecoveryCode();
        recoveryCode.setCodeHash(hash("ABCDEFGHIJKLMNOPQRSTUVWX"));
        when(challengeRepository.findByTokenHash(hash(CHALLENGE))).thenReturn(Optional.of(challenge));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(recoveryCodeRepository.findByUser_IdAndCodeHash(
                user.getId(), hash("ABCDEFGHIJKLMNOPQRSTUVWX"))).thenReturn(Optional.of(recoveryCode));

        var authenticatedUser = mfaService.completeLoginChallenge(CHALLENGE, RECOVERY_CODE);

        assertTrue(authenticatedUser.isPresent());
        assertEquals(user, authenticatedUser.get());
        verify(recoveryCodeRepository).delete(recoveryCode);
        verify(challengeRepository).delete(challenge);
    }

    @Test
    void incrementsFailedChallengeAttemptsWhenFactorIsInvalid() throws Exception {
        challenge.setTokenHash(hash(CHALLENGE));
        when(challengeRepository.findByTokenHash(hash(CHALLENGE))).thenReturn(Optional.of(challenge));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(recoveryCodeRepository.findByUser_IdAndCodeHash(any(), any())).thenReturn(Optional.empty());

        var result = mfaService.completeLoginChallenge(CHALLENGE, "000000");

        assertTrue(result.isEmpty());
        assertEquals(1, challenge.getFailedAttempts());
        assertFalse(challenge.getExpiresAt().isBefore(NOW));
    }

    @Test
    void acceptsTotpOnceAndRejectsReplay() throws Exception {
        String secret = TotpService.encodeBase32(
                "12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        user.setTotpSecretEncrypted(new MfaSecretCipher(
                java.util.Base64.getEncoder().encodeToString(new byte[32])).encrypt(secret));
        String code = TotpService.generateCode(
                "12345678901234567890".getBytes(StandardCharsets.US_ASCII),
                NOW.getEpochSecond() / 30);

        challenge.setTokenHash(hash(CHALLENGE));
        MfaLoginChallenge replayChallenge = new MfaLoginChallenge();
        replayChallenge.setUser(user);
        replayChallenge.setCreatedAt(NOW);
        replayChallenge.setExpiresAt(NOW.plusSeconds(300));
        replayChallenge.setFailedAttempts(0);
        replayChallenge.setTokenHash(hash("second-challenge"));
        when(challengeRepository.findByTokenHash(hash(CHALLENGE))).thenReturn(Optional.of(challenge));
        when(challengeRepository.findByTokenHash(hash("second-challenge")))
                .thenReturn(Optional.of(replayChallenge));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(recoveryCodeRepository.findByUser_IdAndCodeHash(user.getId(), hash(code)))
                .thenReturn(Optional.empty());

        var result = mfaService.completeLoginChallenge(CHALLENGE, code);
        var replayResult = mfaService.completeLoginChallenge("second-challenge", code);

        assertTrue(result.isPresent());
        assertTrue(replayResult.isEmpty());
        assertEquals(NOW.getEpochSecond() / 30, user.getTotpLastUsedStep());
        verify(challengeRepository).delete(challenge);
    }

    @Test
    void confirmsEnrollmentAndReturnsRecoveryCodesOnce() throws Exception {
        String secret = TotpService.encodeBase32(
                "12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        var cipher = new MfaSecretCipher(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        user.setMfaEnabled(false);
        user.setPendingTotpSecretEncrypted(cipher.encrypt(secret));
        user.setPendingTotpExpiresAt(NOW.plusSeconds(300));
        String code = TotpService.generateCode(
                "12345678901234567890".getBytes(StandardCharsets.US_ASCII),
                NOW.getEpochSecond() / 30);
        when(userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("customer", "customer"))
                .thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        List<String> recoveryCodes = mfaService.confirmTotpEnrollment("customer", code);

        assertTrue(user.isMfaEnabled());
        assertTrue(user.getPendingTotpSecretEncrypted() == null);
        assertEquals(10, recoveryCodes.size());
        verify(recoveryCodeRepository).deleteAllByUser_Id(user.getId());
        verify(recoveryCodeRepository).saveAll(any());
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
