package com.upcraft.stay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Main Spring Boot application entrypoint for Upcraft PG.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class StayApplication {

    public static void main(String[] args) {
        SpringApplication.run(StayApplication.class, args);
    }
}
