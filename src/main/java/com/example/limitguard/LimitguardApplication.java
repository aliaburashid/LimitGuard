package com.example.limitguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LimitguardApplication {

	public static void main(String[] args) {
		SpringApplication.run(LimitguardApplication.class, args);
	}

}
