package com.example.pickdispatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PickDispatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(PickDispatchApplication.class, args);
    }
}
