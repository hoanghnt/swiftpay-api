package com.hoanghnt.swiftpay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "app.startup.fail-fast=false")
class SwiftPayApplicationTests {

	@Test
	void contextLoads() {
	}

}
