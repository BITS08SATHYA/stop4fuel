package com.stopforfuel.config;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    /**
     * Fallback signing key, used only when {@code APP_JWT_SECRET} is absent. It lives in source
     * control, so any environment running on it is trivially forgeable — a valid session token
     * for any role can be minted by anyone who can read this file. Deployed environments must
     * supply a real secret; {@link #warnIfDefaultSecret()} shouts if they don't.
     */
    static final String INSECURE_DEFAULT_SECRET = "stopforfuel-dev-secret-key-min-32-chars!!";

    @Value("${app.jwt.secret:" + INSECURE_DEFAULT_SECRET + "}")
    private String secret;

    private final Environment environment;

    public JwtTokenProvider(Environment environment) {
        this.environment = environment;
    }

    private static final long EXPIRATION_MS = 8 * 60 * 60 * 1000; // 8 hours
    private static final long MFA_TOKEN_EXPIRATION_MS = 5 * 60 * 1000; // 5 minutes
    public static final String MFA_PURPOSE = "mfa_pending";

    /**
     * Stage 1 of hardening: warn only, so a misconfigured deploy degrades loudly instead of
     * refusing to boot. Once prod is confirmed to be running on a real secret this becomes a
     * hard startup failure on non-dev profiles.
     */
    @PostConstruct
    void warnIfDefaultSecret() {
        if (!INSECURE_DEFAULT_SECRET.equals(secret)) {
            return;
        }
        boolean devProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        if (devProfile) {
            log.warn("app.jwt.secret is the built-in default. Fine for local dev; never for a deployed environment.");
        } else {
            log.error("SECURITY: app.jwt.secret is the built-in default from source control on profile(s) {}. "
                            + "Session tokens are forgeable by anyone with repo access. "
                            + "Set APP_JWT_SECRET (Secrets Manager: stopforfuel/jwt-secret) immediately.",
                    Arrays.toString(environment.getActiveProfiles()));
        }
    }

    public String generateToken(Long userId, String role, Long scid, String name, String phone, String designation) {
        try {
            JWSSigner signer = new MACSigner(secret.getBytes());

            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .subject(String.valueOf(userId))
                    .claim("custom:role", role)
                    .claim("custom:scid", String.valueOf(scid))
                    .claim("name", name)
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + EXPIRATION_MS));

            if (phone != null) {
                claimsBuilder.claim("phone", phone);
            }
            if (designation != null) {
                claimsBuilder.claim("designation", designation);
            }

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claimsBuilder.build()
            );
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    /**
     * Short-lived token bridging the two login steps. It proves the passcode was already
     * verified, so step 2 (TOTP verify) can trust the user id without re-checking the passcode.
     * For first-time enrollment, the not-yet-persisted (already encrypted) secret rides along in
     * the {@code enroll} claim, keeping the flow stateless until the user confirms a code.
     */
    public String generateMfaToken(Long userId, String encryptedEnrollSecret) {
        try {
            JWSSigner signer = new MACSigner(secret.getBytes());

            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .subject(String.valueOf(userId))
                    .claim("purpose", MFA_PURPOSE)
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + MFA_TOKEN_EXPIRATION_MS));

            if (encryptedEnrollSecret != null) {
                claimsBuilder.claim("enroll", encryptedEnrollSecret);
            }

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claimsBuilder.build()
            );
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to generate MFA token", e);
        }
    }

    public JWTClaimsSet validateToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(secret.getBytes());

            if (!signedJWT.verify(verifier)) {
                return null;
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims.getExpirationTime().before(new Date())) {
                return null;
            }

            return claims;
        } catch (Exception e) {
            return null;
        }
    }
}
