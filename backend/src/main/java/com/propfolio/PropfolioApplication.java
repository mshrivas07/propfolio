package com.propfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PropfolioApplication {

    public static void main(String[] args) {
        SpringApplication.run(PropfolioApplication.class, args);
    }
}
