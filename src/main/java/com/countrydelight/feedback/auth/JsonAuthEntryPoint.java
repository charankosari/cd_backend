package com.countrydelight.feedback.auth;

import com.countrydelight.feedback.common.GlobalExceptionHandler.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;


@Component
public class JsonAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper json;

    public JsonAuthEntryPoint(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        boolean badToken = ex instanceof InvalidBearerTokenException;
        String code = badToken ? "INVALID_TOKEN" : "UNAUTHORIZED";
        String message = badToken
                ? "Login expired or token is invalid. Please log in again"
                : "Login required. Send header 'Authorization: Bearer <token>'";
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(),
                new ApiError(Instant.now(), 401, code, message, request.getRequestURI()));
    }
}
