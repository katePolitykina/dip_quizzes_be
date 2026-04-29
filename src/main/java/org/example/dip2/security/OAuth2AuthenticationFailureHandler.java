package org.example.dip2.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.example.dip2.config.OAuthProperties;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final OAuthProperties oAuthProperties;

    public OAuth2AuthenticationFailureHandler(OAuthProperties oAuthProperties) {
        this.oAuthProperties = oAuthProperties;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        String redirectUrl = UriComponentsBuilder
                .fromUriString(oAuthProperties.getFrontendSuccessRedirect())
                .queryParam("error", "oauth_login_failed")
                .queryParam("message", exception.getMessage())
                .build()
                .encode()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
