package com.stopforfuel.backend.config;

import org.springframework.context.annotation.Configuration;

/**
 * Seed data is now handled by SQL script at resources/db/seed-data.sql
 * which runs automatically on every application boot via spring.sql.init.
 */
@Configuration
public class DataInitializer {
}
