package org.example.dip2.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.oauth")
public class OAuthProperties {

    private String frontendSuccessRedirect = "http://localhost:5173/oauth2/callback";
}
