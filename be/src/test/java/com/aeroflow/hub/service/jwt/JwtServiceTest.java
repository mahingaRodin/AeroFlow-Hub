package com.aeroflow.hub.service.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

class JwtServiceTest {

    @Test
    void createsAndValidatesTokenWithStandardBase64SigningKey() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);
        var service = new JwtService(secret, 900_000);
        var user = User.withUsername("crew.user").password("not-used").authorities("ROLE_CREW").build();

        String token = service.generateToken(user);

        assertEquals("crew.user", service.extractUsername(token));
        assertTrue(service.isTokenValid(token, user));
    }

    @Test
    void rejectsPlaceholderSecretWithActionableConfigurationError() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new JwtService("replace-with-a-generated-base64-key", 900_000));

        assertTrue(exception.getMessage().contains("at least 32 bytes"));
        assertTrue(exception.getMessage().contains("openssl rand -base64 32"));
    }

    @Test
    void rejectsMalformedBase64WithActionableConfigurationError() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new JwtService("not a base64 secret!!!", 900_000));

        assertTrue(exception.getMessage().contains("valid standard Base64 or Base64URL"));
        assertTrue(exception.getMessage().contains("IntelliJ environment-variable override"));
    }

    @Test
    void createsAndValidatesTokenWithBase64UrlSigningKey() {
        byte[] keyBytes = new byte[32];
        keyBytes[0] = (byte) 0xfb;
        keyBytes[1] = (byte) 0xff;
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);
        var service = new JwtService(secret, 900_000);
        var user = User.withUsername("crew.user").password("not-used").authorities("ROLE_CREW").build();

        String token = service.generateToken(user);

        assertEquals("crew.user", service.extractUsername(token));
        assertTrue(service.isTokenValid(token, user));
    }

    @Test
    void rejectsDecodedKeysShorterThan256Bits() {
        String weakSecret = Base64.getEncoder().encodeToString(new byte[16]);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new JwtService(weakSecret, 900_000));

        assertTrue(exception.getMessage().contains("at least 32 bytes"));
    }

    @Test
    void rejectsNonPositiveTokenExpiration() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new JwtService(secret, 0));

        assertTrue(exception.getMessage().contains("greater than zero"));
    }
}
