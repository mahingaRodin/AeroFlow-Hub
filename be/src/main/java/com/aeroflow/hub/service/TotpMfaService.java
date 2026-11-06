package com.aeroflow.hub.service;

import com.aeroflow.hub.model.MfaLoginChallenge;
import com.aeroflow.hub.model.MfaRecoveryCode;
import com.aeroflow.hub.model.User;
import com.aeroflow.hub.exception.MfaEnrollmentStateException;
import com.aeroflow.hub.repository.MfaLoginChallengeRepository;
import com.aeroflow.hub.repository.MfaRecoveryCodeRepository;
import com.aeroflow.hub.repository.UserRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TotpMfaService {

    private static final Duration LOGIN_CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final Duration ENROLLMENT_TTL = Duration.ofMinutes(10);
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int MAX_ENROLLMENT_ATTEMPTS = 5;
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final MfaLoginChallengeRepository challengeRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final MfaSecretCipher secretCipher;
    private final TotpService totpService;
    private final Clock clock;

    public TotpMfaService(
            UserRepository userRepository,
            MfaLoginChallengeRepository challengeRepository,
            MfaRecoveryCodeRepository recoveryCodeRepository,
            PasswordEncoder passwordEncoder,
            MfaSecretCipher secretCipher,
            TotpService totpService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.challengeRepository = challengeRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.secretCipher = secretCipher;
        this.totpService = totpService;
        this.clock = clock;
    }

    @Transactional
    public String createLoginChallenge(User user) {
        challengeRepository.deleteAllByUser_Id(user.getId());

        String rawToken = generateOpaqueToken();
        MfaLoginChallenge challenge = new MfaLoginChallenge();
        challenge.setUser(user);
        challenge.setTokenHash(hash(rawToken));
        challenge.setCreatedAt(clock.instant());
        challenge.setExpiresAt(clock.instant().plus(LOGIN_CHALLENGE_TTL));
        challenge.setFailedAttempts(0);
        challengeRepository.save(challenge);
        return rawToken;
    }

    @Transactional(readOnly = true)
    public boolean isTotpEnabled(String identifier) {
        return requireUser(identifier).isMfaEnabled();
    }

    @Transactional
    public Optional<User> completeLoginChallenge(String rawChallenge, String code) {
        if (!StringUtils.hasText(rawChallenge) || !StringUtils.hasText(code)) {
            return Optional.empty();
        }

        Optional<MfaLoginChallenge> maybeChallenge = challengeRepository.findByTokenHash(hash(rawChallenge.strip()));
        if (maybeChallenge.isEmpty()) {
            return Optional.empty();
        }
        MfaLoginChallenge challenge = maybeChallenge.get();
        Instant now = clock.instant();
        if (!challenge.getExpiresAt().isAfter(now)
                || challenge.getFailedAttempts() >= MAX_LOGIN_ATTEMPTS) {
            challengeRepository.delete(challenge);
            return Optional.empty();
        }

        UUID userId = challenge.getUser().getId();
        Optional<User> maybeUser = userRepository.findByIdForUpdate(userId);
        if (maybeUser.isEmpty()) {
            challengeRepository.delete(challenge);
            return Optional.empty();
        }
        User user = maybeUser.get();
        if (!user.isEnabled() || !user.isMfaEnabled()) {
            challengeRepository.delete(challenge);
            return Optional.empty();
        }

        OptionalLong matchedStep = totpService.matchingStep(
                secretCipher.decrypt(user.getTotpSecretEncrypted()),
                code.strip(),
                now,
                user.getTotpLastUsedStep());
        if (matchedStep.isPresent()) {
            user.setTotpLastUsedStep(matchedStep.getAsLong());
            challengeRepository.delete(challenge);
            return Optional.of(user);
        }

        Optional<MfaRecoveryCode> recoveryCode = recoveryCodeRepository.findByUser_IdAndCodeHash(
                userId, hash(normalizeRecoveryCode(code)));
        if (recoveryCode.isPresent()) {
            recoveryCodeRepository.delete(recoveryCode.get());
            challengeRepository.delete(challenge);
            return Optional.of(user);
        }

        challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
        if (challenge.getFailedAttempts() >= MAX_LOGIN_ATTEMPTS) {
            challengeRepository.delete(challenge);
        }
        return Optional.empty();
    }

    @Transactional
    public TotpSetup beginTotpEnrollment(String identifier, String currentPassword) {
        User user = requireUser(identifier);
        verifyPassword(user, currentPassword);
        if (user.isMfaEnabled()) {
            throw new MfaEnrollmentStateException("Authenticator-app MFA is already enabled");
        }

        String secret = totpService.generateSecret();
        user.setPendingTotpSecretEncrypted(secretCipher.encrypt(secret));
        user.setPendingTotpExpiresAt(clock.instant().plus(ENROLLMENT_TTL));
        user.setPendingTotpFailedAttempts(0);
        String label = URLEncoder.encode("AeroFlow Hub:" + user.getEmail(), StandardCharsets.UTF_8);
        String issuer = URLEncoder.encode("AeroFlow Hub", StandardCharsets.UTF_8);
        String otpauthUri = "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + issuer
                + "&algorithm=SHA1&digits=6&period=30";
        return new TotpSetup(secret, otpauthUri, user.getPendingTotpExpiresAt());
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public List<String> confirmTotpEnrollment(String identifier, String code) {
        User user = requireUserForUpdate(identifier);
        Instant now = clock.instant();
        if (!StringUtils.hasText(user.getPendingTotpSecretEncrypted())
                || user.getPendingTotpExpiresAt() == null
                || !user.getPendingTotpExpiresAt().isAfter(now)) {
            clearPendingEnrollment(user);
            throw new MfaEnrollmentStateException("Authenticator enrollment has expired; start again");
        }

        String secret = secretCipher.decrypt(user.getPendingTotpSecretEncrypted());
        OptionalLong matchedStep = totpService.matchingStep(secret, code, now, -1);
        if (matchedStep.isEmpty()) {
            int failedAttempts = user.getPendingTotpFailedAttempts() + 1;
            user.setPendingTotpFailedAttempts(failedAttempts);
            if (failedAttempts >= MAX_ENROLLMENT_ATTEMPTS) {
                clearPendingEnrollment(user);
            }
            throw new BadCredentialsException("Invalid authenticator code");
        }

        user.setTotpSecretEncrypted(secretCipher.encrypt(secret));
        user.setTotpLastUsedStep(matchedStep.getAsLong());
        user.setMfaEnabled(true);
        user.setPendingTotpFailedAttempts(0);
        clearPendingEnrollment(user);

        recoveryCodeRepository.deleteAllByUser_Id(user.getId());
        List<String> recoveryCodes = generateRecoveryCodes(user);
        return List.copyOf(recoveryCodes);
    }

    @Transactional
    public void disableTotp(String identifier, String currentPassword, String code) {
        User user = requireUserForUpdate(identifier);
        verifyPassword(user, currentPassword);
        if (!user.isMfaEnabled() || !verifyTotpOrConsumeRecoveryCode(user, code)) {
            throw new BadCredentialsException("Authentication failed");
        }

        user.setMfaEnabled(false);
        user.setTotpSecretEncrypted(null);
        user.setTotpLastUsedStep(-1);
        clearPendingEnrollment(user);
        challengeRepository.deleteAllByUser_Id(user.getId());
        recoveryCodeRepository.deleteAllByUser_Id(user.getId());
    }

    private List<String> generateRecoveryCodes(User user) {
        List<MfaRecoveryCode> entities = new ArrayList<>(RECOVERY_CODE_COUNT);
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int index = 0; index < RECOVERY_CODE_COUNT; index++) {
            String code = formatRecoveryCode(generateOpaqueToken());
            MfaRecoveryCode entity = new MfaRecoveryCode();
            entity.setUser(user);
            entity.setCodeHash(hash(normalizeRecoveryCode(code)));
            entity.setCreatedAt(clock.instant());
            entities.add(entity);
            codes.add(code);
        }
        recoveryCodeRepository.saveAll(entities);
        return codes;
    }

    private boolean verifyTotpOrConsumeRecoveryCode(User user, String code) {
        OptionalLong matchingStep = totpService.matchingStep(
                secretCipher.decrypt(user.getTotpSecretEncrypted()),
                code == null ? "" : code.strip(),
                clock.instant(),
                user.getTotpLastUsedStep());
        if (matchingStep.isPresent()) {
            user.setTotpLastUsedStep(matchingStep.getAsLong());
            return true;
        }

        return recoveryCodeRepository.findByUser_IdAndCodeHash(
                        user.getId(), hash(normalizeRecoveryCode(code)))
                .map(recovery -> {
                    recoveryCodeRepository.delete(recovery);
                    return true;
                })
                .orElse(false);
    }

    private User requireUser(String identifier) {
        return userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(identifier, identifier)
                .orElseThrow(() -> new BadCredentialsException("Authentication failed"));
    }

    private User requireUserForUpdate(String identifier) {
        User user = requireUser(identifier);
        return userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new BadCredentialsException("Authentication failed"));
    }

    private void verifyPassword(User user, String password) {
        if (!StringUtils.hasText(password) || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("Authentication failed");
        }
    }

    private static void clearPendingEnrollment(User user) {
        user.setPendingTotpSecretEncrypted(null);
        user.setPendingTotpExpiresAt(null);
        user.setPendingTotpFailedAttempts(0);
    }

    private static String generateOpaqueToken() {
        byte[] token = new byte[24];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private static String formatRecoveryCode(String code) {
        StringBuilder formatted = new StringBuilder(code.length() + code.length() / 5);
        for (int index = 0; index < code.length(); index++) {
            if (index > 0 && index % 5 == 0) {
                formatted.append('-');
            }
            formatted.append(code.charAt(index));
        }
        return formatted.toString();
    }

    private static String normalizeRecoveryCode(String code) {
        return code == null
                ? ""
                : code.replace("-", "").replace(" ", "").strip().toUpperCase(Locale.ROOT);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record TotpSetup(String secret, String otpauthUri, Instant expiresAt) {
    }
}
