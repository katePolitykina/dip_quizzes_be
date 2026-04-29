package org.example.dip2.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.rooms")
public class RoomProperties {

    private Duration ttl;
    private int pinLength;
    private String store;
    private Duration lockTimeout;
}
