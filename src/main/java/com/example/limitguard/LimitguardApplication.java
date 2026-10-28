package com.example.limitguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// Enables scheduled tasks, such as checking expired credit requests.
@EnableScheduling
// Allows Spring to execute @Async methods on background threads.
@EnableAsync
public class LimitguardApplication {

	public static void main(String[] args) {
		SpringApplication.run(LimitguardApplication.class, args);
	}

}
