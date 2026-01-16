package com.pasarela;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PasarelaApplication {
    public static void main(String[] args) {
        SpringApplication.run(PasarelaApplication.class, args);
    }
}
