package com.countrydelight.feedback.auth;

import com.countrydelight.feedback.domain.Customer;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Creates the login token (a JWT) after a successful OTP check.
 *
 * <p>A JWT has 3 parts, header.payload.signature. The payload (claims) says who the user is
 * (sub = customer id) and when the token expires. The signature is made with JWT_SECRET, so if anyone
 * edits the payload (e.g. changes sub to another customer), the signature no longer matches
 * and SecurityConfig rejects the token.
 */
@Service
public class TokenService {

    public record IssuedToken(String value, Instant expiresAt, long expiresInSeconds) {
    }

    private final JwtEncoder encoder;
    private final AuthProperties.Jwt config;

    public TokenService(JwtEncoder encoder, AuthProperties props) {
        this.encoder = encoder;
        this.config = props.jwt();
    }

    public IssuedToken issueFor(Customer customer) {
        Instant now = Instant.now();
        Duration ttl = Duration.ofHours(config.ttlHours());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(config.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(String.valueOf(customer.getId()))   // who: read back by @CurrentCustomer
                .claim("phone", customer.getPhone())
                .claim("role", "CUSTOMER")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, now.plus(ttl), ttl.toSeconds());
    }
}
