package com.stopforfuel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StopForFuelApplication {

	public static void main(String[] args) {
		SpringApplication.run(StopForFuelApplication.class, args);
	}

}
