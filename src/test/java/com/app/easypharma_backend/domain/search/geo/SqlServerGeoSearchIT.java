package com.app.easypharma_backend.domain.search.geo;

import com.app.easypharma_backend.config.TestMailConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Profil sqlserver : migrations Flyway T-SQL (db/sqlserver), validation Hibernate, STDistance. */
@SpringBootTest
@ActiveProfiles({ "test", "sqlserver" })
@Import(TestMailConfiguration.class)
@Testcontainers
class SqlServerGeoSearchIT extends GeoSearchContract {

    @Container
    static final MSSQLServerContainer<?> DB = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
}
