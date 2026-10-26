package com.app.easypharma_backend.infrastructure.config;

import com.app.easypharma_backend.config.TestMailConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sans jeton : la santé reste lisible (sondes), les métriques et la console H2 non. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMailConfiguration.class)
class OperationalEndpointsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsReachableWithoutToken() throws Exception {
        int httpStatus = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();
        // 200 ou 503 selon les dépendances du profil de test ; jamais 401 ou 403.
        assertThat(httpStatus).isNotIn(401, 403);
    }

    @Test
    void metricsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void prometheusRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }
}
