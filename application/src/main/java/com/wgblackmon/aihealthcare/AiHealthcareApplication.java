package com.wgblackmon.aihealthcare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point for the AIHealthcare newsletter + intelligence platform.
 *
 * Enables component scanning across all sub-packages, auto-configuration, and the full
 * Spring context including JPA, security, scheduling, and AI adapters.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2025-01-27
 * @updated 2026-10-05
 */
@SpringBootApplication
public class AiHealthcareApplication {

    public static void main(String[] args) {

        SpringApplication.run(AiHealthcareApplication.class, args);
    }
}