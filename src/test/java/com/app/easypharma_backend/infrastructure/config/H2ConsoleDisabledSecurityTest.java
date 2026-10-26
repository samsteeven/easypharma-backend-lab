package com.app.easypharma_backend.infrastructure.config;

import com.app.easypharma_backend.config.TestMailConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Console H2 éteinte (comme par défaut) : son chemin exige un jeton, il n'est plus ouvert à tous. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMailConfiguration.class)
@TestPropertySource(properties = "spring.h2.console.enabled=false")
class H2ConsoleDisabledSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void h2ConsolePathRequiresAuthenticationWhenDisabled() throws Exception {
        mockMvc.perform(get("/h2-console/")).andExpect(status().isUnauthorized());
    }
}
