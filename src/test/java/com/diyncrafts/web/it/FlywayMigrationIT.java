package com.diyncrafts.web.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Runs the real Flyway migrations against MySQL 8.4 for (a) an existing database created by the old
 * Hibernate ddl-auto=update setup and (b) an empty database.
 */
@Testcontainers
class FlywayMigrationIT {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test
    void upgradesLegacyDatabaseWithoutLosingDataAndAllowsSeveralGuidesPerUser() throws Exception {
        String schema = createSchema("legacy");
        JdbcTemplate jdbc = jdbc(schema);
        // The legacy schema is exactly V1 (captured from Hibernate's ddl-auto output).
        executeScript(schema, Files.readString(Path.of("src/main/resources/db/migration/V1__baseline_legacy_schema.sql"),
                StandardCharsets.UTF_8));
        byte[] userId = uuidBytes(UUID.randomUUID());
        jdbc.update("INSERT INTO user_account (id, email, enabled, password, role, username) VALUES (?, 'a@x.io', 1, '$2a$10$h', 'ROLE_USER', 'alice')", userId);
        jdbc.update("INSERT INTO video (id, title, thumbnail_url, upload_date, video_url, views, user_id) VALUES (1, 'Birdhouse', 't', '2024-01-01', 'v', 7, ?)", userId);
        jdbc.update("INSERT INTO guide (title, content, user_id, video_id) VALUES ('First', 'c', ?, 1)", userId);
        jdbc.update("INSERT INTO task (task_id, progress, status) VALUES ('old-task', 100, 2)");
        jdbc.update("INSERT INTO category (name, description) VALUES ('Woodworking', 'custom description')");
        // Demonstrates the defect being fixed.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO guide (title, content, user_id, video_id) VALUES ('Second', 'c', ?, 1)", userId))
                .hasMessageContaining("Duplicate entry");

        flyway(schema).migrate();
        assertThat(flyway(schema).info().current().getVersion().getVersion()).isEqualTo("6");
        assertThat(flyway(schema).info().applied()).extracting(m -> m.getVersion().getVersion())
                .containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(flyway(schema).info().applied()[0].getType().name()).isEqualTo("BASELINE");

        jdbc.update("INSERT INTO guide (title, content, user_id, video_id) VALUES ('Second', 'c', ?, 1)", userId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guide WHERE user_id = ?", Integer.class, userId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT views FROM video WHERE id = 1", Long.class)).isEqualTo(7L);
        assertThat(jdbc.queryForObject("SELECT status FROM task WHERE task_id = 'old-task'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT video_id FROM task WHERE task_id = 'old-task'", Long.class)).isNull();
        // New columns/tables exist and URLs are nullable.
        jdbc.update("INSERT INTO video (title, upload_date, views, user_id) VALUES ('Pending', '2024-01-02', 0, ?)", userId);
        jdbc.update("INSERT INTO video_daily_views (video_id, view_date, views) VALUES (1, '2024-01-03', 1)");
        // Seed data does not duplicate or overwrite existing categories.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE name = 'Woodworking'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT description FROM category WHERE name = 'Woodworking'", String.class))
                .isEqualTo("custom description");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM category", Integer.class)).isEqualTo(7);
    }

    @Test
    void migrationsAreRerunnableAfterPartialApplication() throws Exception {
        String schema = createSchema("rerun");
        executeScript(schema, Files.readString(Path.of("src/main/resources/db/migration/V1__baseline_legacy_schema.sql"),
                StandardCharsets.UTF_8));
        for (String script : List.of("V2__allow_multiple_guides_per_user.sql", "V3__task_video_reference.sql")) {
            String sql = Files.readString(Path.of("src/main/resources/db/migration/" + script), StandardCharsets.UTF_8);
            executeScript(schema, sql);
            executeScript(schema, sql);
        }
        flyway(schema).migrate();
        assertThat(flyway(schema).info().current().getVersion().getVersion()).isEqualTo("6");
    }

    @Test
    void createsSchemaForEmptyDatabase() throws Exception {
        String schema = createSchema("fresh");
        flyway(schema).migrate();
        assertThat(flyway(schema).info().applied()).extracting(m -> m.getType().name())
                .containsOnly("SQL");
        assertThat(jdbc(schema).queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = ? AND table_name = 'guide' "
                        + "AND column_name = 'user_id' AND non_unique = 0", Integer.class, schema)).isZero();
        assertThat(jdbc(schema).queryForObject("SELECT COUNT(*) FROM category", Integer.class)).isEqualTo(7);
    }

    private static Flyway flyway(String schema) {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl().replace("/" + MYSQL.getDatabaseName(), "/" + schema), "root", MYSQL.getPassword())
                .locations("filesystem:src/main/resources/db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
    }

    private static String createSchema(String name) throws Exception {
        try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
                Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + name);
        }
        return name;
    }

    private static JdbcTemplate jdbc(String schema) {
        return new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl().replace("/" + MYSQL.getDatabaseName(), "/" + schema), "root", MYSQL.getPassword()));
    }

    private static void executeScript(String schema, String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(
                MYSQL.getJdbcUrl().replace("/" + MYSQL.getDatabaseName(), "/" + schema), "root", MYSQL.getPassword());
                Statement s = c.createStatement()) {
            for (String statement : sql.replaceAll("(?m)^--.*$", "").split(";")) {
                if (!statement.isBlank()) {
                    s.execute(statement);
                }
            }
        }
    }

    private static byte[] uuidBytes(UUID uuid) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }
}
