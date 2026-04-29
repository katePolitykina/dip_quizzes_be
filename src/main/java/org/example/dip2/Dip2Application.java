package org.example.dip2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Dip2Application {

    public static void main(String[] args) {
        SpringApplication.run(Dip2Application.class, args);
    }

}
