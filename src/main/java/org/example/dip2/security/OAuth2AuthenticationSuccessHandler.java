package org.example.dip2.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.example.dip2.config.OAuthProperties;
import org.example.dip2.dto.auth.AuthResponse;
import org.example.dip2.service.OAuth2LoginService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2LoginService oAuth2LoginService;
    private final OAuthProperties oAuthProperties;
    private final ObjectMapper objectMapper;

    public OAuth2AuthenticationSuccessHandler(
            OAuth2LoginService oAuth2LoginService,
            OAuthProperties oAuthProperties,
            ObjectMapper objectMapper
    ) {
        this.oAuth2LoginService = oAuth2LoginService;
        this.oAuthProperties = oAuthProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            throw new ServletException("Unsupported OAuth2 authentication");
        }

        AuthResponse authResponse = oAuth2LoginService.completeGoogleLogin(oauthToken);
        String redirectUrl = UriComponentsBuilder
                .fromUriString(oAuthProperties.getFrontendSuccessRedirect())
                .queryParam("auth", toJson(authResponse))
                .build()
                .encode()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }

    private String toJson(AuthResponse authResponse) throws JsonProcessingException {
        return objectMapper.writeValueAsString(authResponse);
    }
}
