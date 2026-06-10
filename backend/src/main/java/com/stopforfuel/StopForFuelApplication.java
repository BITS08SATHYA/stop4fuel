package com.stopforfuel;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class StopForFuelApplication {

	/**
	 * Force the JVM default timezone to IST so that LocalDateTime.now() and
	 * Hibernate @CreationTimestamp produce India local time regardless of the
	 * host/container clock (AWS ECS containers default to UTC). Without this,
	 * bill timestamps were stored 5h30m behind real local time.
	 */
	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
	}

	public static void main(String[] args) {
		SpringApplication.run(StopForFuelApplication.class, args);
	}

}
