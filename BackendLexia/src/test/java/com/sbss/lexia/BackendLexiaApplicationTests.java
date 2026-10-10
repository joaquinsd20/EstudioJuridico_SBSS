package com.sbss.lexia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Levanta el contexto completo, y eso arranca Flyway contra MySQL. Sin base
 * fallaba con "Access denied ... (using password: NO)" y dejaba en rojo todo
 * {@code ./mvnw test}, aunque el resto de pruebas sin base pasara. Se salta si
 * LEXIA_TEST_DB no esta definida, igual que las *IT.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LEXIA_TEST_DB", matches = ".+")
class BackendLexiaApplicationTests {

	@Test
	void contextLoads() {
	}

}
