package com.aeroflow.hub.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aeroflow.hub.model.User;
import com.aeroflow.hub.service.CustomerRegistrationService;
import com.aeroflow.hub.service.TotpMfaService;
import com.aeroflow.hub.service.jwt.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomerRegistrationService registrationService;

    @Mock
    private TotpMfaService totpMfaService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authenticationManager, jwtService, registrationService, totpMfaService);
    }

    @Test
    void doesNotIssueAccessTokenBeforeMfaChallengeIsCompleted() {
        User user = new User();
        user.setUsername("customer-1");
        user.setMfaEnabled(true);
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        when(totpMfaService.createLoginChallenge(user)).thenReturn("opaque-challenge");

        var response = controller.login(new AuthController.LoginRequest("customer@example.com", "password"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody().accessToken());
        assertTrue(response.getBody().mfaRequired());
        assertEquals("opaque-challenge", response.getBody().challengeToken());
        verify(jwtService, never()).generateToken(user);
    }

    @Test
    void issuesAccessTokenOnlyAfterMfaChallengeSucceeds() {
        User user = new User();
        when(totpMfaService.completeLoginChallenge("challenge", "123456"))
                .thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("access-token");

        var response = controller.completeMfaLogin(
                new AuthController.MfaLoginRequest("challenge", "123456"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("access-token", response.getBody().accessToken());
        assertFalse(response.getBody().mfaRequired());
        assertNull(response.getBody().challengeToken());
        verify(jwtService).generateToken(user);
    }
}
