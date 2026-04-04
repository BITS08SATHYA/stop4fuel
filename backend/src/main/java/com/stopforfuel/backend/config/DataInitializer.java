package com.stopforfuel.backend.config;

import org.springframework.context.annotation.Configuration;

/**
 * Seed data is now handled by SQL script at resources/db/seed-data.sql
 * Payment modes are now a Java enum — no seeding needed.
 */
@Configuration
public class DataInitializer {
}
