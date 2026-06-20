package com.inteliroadmap.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IntelipathBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelipathBackendApplication.class, args);
        System.out.println("Intelipath Backend Application Started");
    }

}
