package com.example.limitguard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
// Uses application-test.properties when running tests
@ActiveProfiles("test")
class LimitguardApplicationTests {

	@Test
	void contextLoads() {
	}

}
