package com.aeroflow.hub.service;

import com.aeroflow.hub.exception.InvalidEmailVerificationTokenException;
import com.aeroflow.hub.model.EmailVerificationToken;
import com.aeroflow.hub.model.Role;
import com.aeroflow.hub.model.User;
import com.aeroflow.hub.repository.EmailVerificationTokenRepository;
import com.aeroflow.hub.repository.RoleRepository;
import com.aeroflow.hub.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CustomerRegistrationService {

    private static final String CUSTOMER_ROLE = "ROLE_CUSTOMER";
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final Duration VERIFICATION_TTL = Duration.ofHours(24);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailNotificationService emailNotificationService;
    private final Clock clock;
    private final String publicBaseUrl;

    public CustomerRegistrationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            EmailVerificationTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            EmailNotificationService emailNotificationService,
            Clock clock,
            @Value("${app.auth.public-base-url:http://localhost:5173}") String publicBaseUrl
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailNotificationService = emailNotificationService;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional
    public void register(String requestedEmail, String password) {
        String email = normalizeEmail(requestedEmail);
        validatePassword(password);

        var existingUser = userRepository.findByEmailIgnoreCase(email);
        if (existingUser.isPresent()) {
            if (!existingUser.get().isEmailVerified()) {
                sendVerification(existingUser.get());
            }
            return;
        }

        Role customerRole = roleRepository.findByName(CUSTOMER_ROLE)
                .orElseThrow(() -> new IllegalStateException("Customer role has not been initialized"));

        User user = new User();
        user.setUsername("customer-" + UUID.randomUUID());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setEnabled(false);
        user.setEmailVerified(false);
        user.setRoles(new HashSet<>(Set.of(customerRole)));
        sendVerification(userRepository.save(user));
    }

    @Transactional
    public void resendVerification(String requestedEmail) {
        if (!StringUtils.hasText(requestedEmail)) {
            return;
        }
        userRepository.findByEmailIgnoreCase(normalizeEmail(requestedEmail))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendVerification);
    }

    private void sendVerification(User user) {
        Instant now = clock.instant();
        EmailVerificationToken token = tokenRepository.findByUser_Id(user.getId())
                .orElseGet(EmailVerificationToken::new);
        if (token.getCreatedAt() != null
                && token.getCreatedAt().plus(RESEND_COOLDOWN).isAfter(now)) {
            return;
        }

        String rawToken = generateToken();
        token.setUser(user);
        token.setTokenHash(hashToken(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(VERIFICATION_TTL));
        tokenRepository.save(token);

        String verificationUrl = publicBaseUrl.replaceAll("/+$", "")
                + "/verify-email?token=" + rawToken;
        emailNotificationService.sendTextEmail(
                user.getEmail(),
                "Verify your AeroFlow Hub email",
                "Use this link to verify your email address. It expires in 24 hours:\n\n"
                        + verificationUrl
                        + "\n\nIf you did not create an AeroFlow Hub account, you can ignore this message.");
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new InvalidEmailVerificationTokenException();
        }

        EmailVerificationToken token = tokenRepository.findByTokenHash(hashToken(rawToken.strip()))
                .filter(candidate -> candidate.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(InvalidEmailVerificationTokenException::new);

        User user = token.getUser();
        user.setEmailVerified(true);
        user.setEnabled(true);
        tokenRepository.delete(token);
    }

    private static String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Email address is required");
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static void validatePassword(String password) {
        int passwordBytes = password == null
                ? 0
                : password.getBytes(StandardCharsets.UTF_8).length;
        if (!StringUtils.hasText(password)
                || password.length() < MIN_PASSWORD_LENGTH
                || passwordBytes > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException(
                    "Password must be at least 12 characters and no more than 72 UTF-8 bytes");
        }
    }

    private static String generateToken() {
        byte[] token = new byte[32];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
