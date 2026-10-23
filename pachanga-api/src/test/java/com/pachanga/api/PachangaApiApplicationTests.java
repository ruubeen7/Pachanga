package com.pachanga.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test") // sin el perfil "dev": no se meten canchas de prueba
class PachangaApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
