package com.countrydelight.feedback.config;

import com.countrydelight.feedback.auth.AuthProperties;
import com.countrydelight.feedback.auth.JsonAuthEntryPoint;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * WHERE AUTH HAPPENS: in a filter that runs BEFORE any controller.
 *
 * <pre>
 *  request ─▶ [Security filter chain] ─▶ controller ─▶ service ─▶ repository
 *               │ 1. Is this URL open (permitAll)? ─ yes ─▶ let it through
 *               │ 2. Else: is there "Authorization: Bearer token"?   no  ─▶ 401
 *               │ 3. Signature valid (JWT_SECRET)? Not expired? Right issuer?  no ─▶ 401
 *               │ 4. Yes: store the token in the SecurityContext ─▶ let it through
 *               ▼
 *            @CurrentCustomer reads the customer id from that stored token
 * </pre>
 * Controllers and services never parse tokens themselves.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JsonAuthEntryPoint entryPoint) throws Exception {
        http
                // No cookies or sessions: every request carries its own token, so CSRF protection isn't needed.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))   // lets the H2 console page render
                .authorizeHttpRequests(auth -> auth
                        // Login / signup: obviously open.
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/auth/otp/request")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/auth/login")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/auth/register")).permitAll()
                        // Quality-team dashboard + admin/testing tools: OPEN for now (decided: no admin login yet).
                        .requestMatchers(antMatcher("/api/dashboard/**")).permitAll()
                        .requestMatchers(antMatcher("/api/admin/**")).permitAll()
                        // Public catalog, uploaded photos (unguessable names), dev tools, error page.
                        .requestMatchers(antMatcher(HttpMethod.GET, "/api/products/**")).permitAll()
                        .requestMatchers(antMatcher("/uploads/**")).permitAll()
                        .requestMatchers(antMatcher("/dashboard.html")).permitAll()
                        .requestMatchers(antMatcher("/h2-console/**")).permitAll()
                        .requestMatchers(antMatcher("/error")).permitAll()
                        // EVERYTHING ELSE (feedback, cart, /api/auth/me) needs a valid token.
                        .anyRequest().authenticated())
                // "Resource server" = check the Bearer JWT on each request using the JwtDecoder bean below.
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()).authenticationEntryPoint(entryPoint))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    /** The shared secret that signs and verifies tokens (HMAC-SHA256). Comes from JWT_SECRET. */
    @Bean
    public SecretKey jwtSigningKey(AuthProperties props) {
        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.auth.jwt.secret (JWT_SECRET) must be at least 32 characters");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** Signs new tokens (used by TokenService at login). */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    /** Verifies incoming tokens: signature + expiry + issuer. Used by the filter chain above. */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey key, AuthProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.jwt().issuer()));
        return decoder;
    }

    /** One-way hashing for OTP codes (same idea as storing password hashes). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
