package com.stopforfuel.backend.service;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MfaServiceTest {

    private final MfaService mfaService = new MfaService("StopForFuel");
    private final MfaCryptoService crypto = new MfaCryptoService("test-mfa-encryption-key");

    private String currentCode(String secret) throws Exception {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        long counter = Math.floorDiv(timeProvider.getTime(), 30);
        return codeGenerator.generate(secret, counter);
    }

    @Test
    void verifiesCurrentCode() throws Exception {
        String secret = mfaService.generateSecret();
        assertNotNull(secret);
        assertTrue(mfaService.verify(secret, currentCode(secret)));
    }

    @Test
    void rejectsWrongCode() {
        String secret = mfaService.generateSecret();
        assertFalse(mfaService.verify(secret, "000000"));
        assertFalse(mfaService.verify(secret, "123456"));
    }

    @Test
    void rejectsNullInputs() {
        assertFalse(mfaService.verify(null, "123456"));
        assertFalse(mfaService.verify("ABC", null));
    }

    @Test
    void buildsQrDataUri() {
        String secret = mfaService.generateSecret();
        String uri = mfaService.buildQrDataUri(secret, "9840011111");
        assertTrue(uri.startsWith("data:image/png;base64,"));
    }

    @Test
    void cryptoRoundTrips() {
        String secret = mfaService.generateSecret();
        String encrypted = crypto.encrypt(secret);
        assertNotEquals(secret, encrypted);
        assertEquals(secret, crypto.decrypt(encrypted));
    }

    @Test
    void cryptoUsesRandomIvSoCiphertextDiffers() {
        String secret = mfaService.generateSecret();
        // Same plaintext encrypts to different ciphertext (random IV) but both decrypt back.
        assertNotEquals(crypto.encrypt(secret), crypto.encrypt(secret));
    }
}
