package com.aeroflow.hub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class MfaSecretCipherTest {

    @Test
    void encryptsAndDecryptsTOTPSecretWithRandomizedCiphertext() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        var cipher = new MfaSecretCipher(key);

        String encrypted = cipher.encrypt("JBSWY3DPEHPK3PXP");

        assertNotEquals("JBSWY3DPEHPK3PXP", encrypted);
        assertEquals("JBSWY3DPEHPK3PXP", cipher.decrypt(encrypted));
        assertNotEquals(encrypted, cipher.encrypt("JBSWY3DPEHPK3PXP"));
    }

    @Test
    void rejectsMissingOrIncorrectlySizedEncryptionKey() {
        assertThrows(IllegalStateException.class, () -> new MfaSecretCipher(""));
        assertThrows(
                IllegalStateException.class,
                () -> new MfaSecretCipher(Base64.getEncoder().encodeToString(new byte[16])));
    }
}
