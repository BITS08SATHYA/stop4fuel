package com.stopforfuel.backend.service;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import dev.samstevens.totp.util.Utils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * TOTP (RFC 6238) helper: generates per-user secrets, builds the QR data-URI shown on the
 * enrollment screen, and verifies 6-digit codes produced by the user's authenticator app.
 * The code generator itself lives in the external authenticator app — this class only
 * issues the shared secret and validates codes against it.
 */
@Service
public class MfaService {

    private final String issuer;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeVerifier codeVerifier;

    public MfaService(@Value("${app.mfa.issuer:StopForFuel}") String issuer) {
        this.issuer = issuer;
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        // Accept the previous/next 30s window too, to tolerate clock drift between phone and server.
        verifier.setAllowedTimePeriodDiscrepancy(1);
        this.codeVerifier = verifier;
    }

    /** Generates a fresh Base32 TOTP secret. */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /**
     * Builds a {@code data:image/png;base64,...} URI encoding the otpauth QR for this secret,
     * ready to drop straight into an &lt;img src&gt; on the enrollment screen.
     */
    public String buildQrDataUri(String secret, String accountLabel) {
        QrData data = new QrData.Builder()
                .label(accountLabel)
                .secret(secret)
                .issuer(issuer)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        try {
            byte[] imageData = qrGenerator.generate(data);
            return Utils.getDataUriForImage(imageData, qrGenerator.getImageMimeType());
        } catch (QrGenerationException e) {
            throw new IllegalStateException("Failed to generate MFA QR code", e);
        }
    }

    /** True when {@code code} is the current (or adjacent-window) TOTP for {@code secret}. */
    public boolean verify(String secret, String code) {
        return secret != null && code != null && codeVerifier.isValidCode(secret, code);
    }
}
