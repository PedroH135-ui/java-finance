package br.com.javafinance;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/** Default: disposable Docker database. Optional fallback: dedicated loopback-only test cluster. */
final class TestDatabase {
    private TestDatabase() {}
    static void configure(DynamicPropertyRegistry registry) {
        String local = System.getProperty("finance.test.url");
        if (local != null) {
            if (!local.equals("jdbc:postgresql://127.0.0.1:55433/java_finance_verify"))
                throw new IllegalArgumentException("The fallback permits only the dedicated local verification database.");
            registry.add("spring.datasource.url", () -> local);
            registry.add("spring.datasource.username", () -> "postgres");
            registry.add("spring.datasource.password", () -> "");
        } else {
            var database = DockerDatabase.INSTANCE;
            registry.add("spring.datasource.url", database::getJdbcUrl);
            registry.add("spring.datasource.username", database::getUsername);
            registry.add("spring.datasource.password", database::getPassword);
        }
    }
    private static class DockerDatabase {
        static final PostgreSQLContainer<?> INSTANCE = start();
        private static PostgreSQLContainer<?> start() {
            var database = new PostgreSQLContainer<>("postgres:16.10-alpine");
            database.start();
            Runtime.getRuntime().addShutdownHook(new Thread(database::stop));
            return database;
        }
    }
}
