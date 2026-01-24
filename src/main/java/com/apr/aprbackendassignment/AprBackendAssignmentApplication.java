package com.apr.aprbackendassignment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class AprBackendAssignmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AprBackendAssignmentApplication.class, args);
    }

}
