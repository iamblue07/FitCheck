package com.sewlect;

import com.sewlect.support.AbstractPostgresIntegrationTest;
import com.sewlect.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.ai.model.chat=none",
		"spring.ai.model.embedding=none",
		WebSliceTestConfig.JWT_SECRET_PROPERTY,
		"r2.endpoint=https://r2.test.invalid",
		"r2.access-key-id=test",
		"r2.secret-access-key=test",
		"r2.bucket=test",
		"fashn.api-key=test",
		"ollama.cloud.api-key=test",
		"deepinfra.api-key=test"
})
class SewlectApplicationTests extends AbstractPostgresIntegrationTest {

	@Test
	void contextLoads() {
	}
}