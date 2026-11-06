package com.aeroflow.hub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aeroflow.hub.exception.InvalidEmailVerificationTokenException;
import com.aeroflow.hub.model.EmailVerificationToken;
import com.aeroflow.hub.model.Role;
import com.aeroflow.hub.model.User;
import com.aeroflow.hub.repository.EmailVerificationTokenRepository;
import com.aeroflow.hub.repository.RoleRepository;
import com.aeroflow.hub.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CustomerRegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");
    private static final String PASSWORD = "StrongCustomerPassword123";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailNotificationService emailNotificationService;

    private CustomerRegistrationService registrationService;

    @BeforeEach
    void setUpServiceWithTestClock() {
        registrationService = new CustomerRegistrationService(
                userRepository,
                roleRepository,
                tokenRepository,
                passwordEncoder,
                emailNotificationService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "https://aeroflow.example.test");
    }

    @Test
    void registersUnverifiedCustomerAndSendsHashedOneTimeVerificationLink() throws Exception {
        Role customerRole = new Role();
        customerRole.setName("ROLE_CUSTOMER");
        when(userRepository.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(java.util.UUID.randomUUID());
            return saved;
        });

        registrationService.register(" Customer@Example.com ", PASSWORD);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User user = userCaptor.getValue();
        assertEquals("customer@example.com", user.getEmail());
        assertTrue(user.getUsername().startsWith("customer-"));
        assertEquals("bcrypt-hash", user.getPassword());
        assertFalse(user.isEmailVerified());
        assertFalse(user.isEnabled());
        assertTrue(user.getRoles().contains(customerRole));

        ArgumentCaptor<EmailVerificationToken> tokenCaptor =
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        EmailVerificationToken storedToken = tokenCaptor.getValue();
        assertEquals(user, storedToken.getUser());
        assertEquals(NOW.plusSeconds(24 * 60 * 60), storedToken.getExpiresAt());

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailNotificationService).sendTextEmail(
                org.mockito.ArgumentMatchers.eq("customer@example.com"),
                org.mockito.ArgumentMatchers.anyString(),
                bodyCaptor.capture());
        Matcher matcher = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(bodyCaptor.getValue());
        assertTrue(matcher.find());
        String rawToken = matcher.group(1);
        assertEquals(hash(rawToken), storedToken.getTokenHash());
        assertFalse(bodyCaptor.getValue().contains(storedToken.getTokenHash()));
    }

    @Test
    void verifiesEmailAndConsumesTheVerificationToken() throws Exception {
        User user = new User();
        user.setEnabled(false);
        user.setEmailVerified(false);
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(hash("single-use-token"));
        token.setExpiresAt(NOW.plusSeconds(60));
        when(tokenRepository.findByTokenHash(hash("single-use-token"))).thenReturn(Optional.of(token));

        registrationService.verifyEmail("single-use-token");

        assertTrue(user.isEnabled());
        assertTrue(user.isEmailVerified());
        verify(tokenRepository).delete(token);
    }

    @Test
    void hidesWhetherAnEmailAlreadyHasAnAccount() {
        User verifiedUser = new User();
        verifiedUser.setEmailVerified(true);
        when(userRepository.findByEmailIgnoreCase("customer@example.com"))
                .thenReturn(Optional.of(verifiedUser));

        registrationService.register("customer@example.com", PASSWORD);

        verify(roleRepository, never()).findByName(any());
        verify(tokenRepository, never()).save(any());
        verify(emailNotificationService, never()).sendTextEmail(any(), any(), any());
    }

    @Test
    void rejectsExpiredVerificationTokens() throws Exception {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setExpiresAt(NOW);
        when(tokenRepository.findByTokenHash(hash("expired-token"))).thenReturn(Optional.of(token));

        assertThrows(
                InvalidEmailVerificationTokenException.class,
                () -> registrationService.verifyEmail("expired-token"));
        verify(tokenRepository, never()).delete(token);
    }

    @Test
    void rejectsWeakPasswordsBeforeCreatingAccounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> registrationService.register("customer@example.com", "short"));
        verify(userRepository, never()).save(any());
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
