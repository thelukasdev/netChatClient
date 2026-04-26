/*
 * Copyright (c) 2026 Lukas Pellny. All rights reserved.
 * NetChat and the source code in this file are proprietary project material
 * created by Lukas Pellny. Copying, publishing, or reusing substantial parts
 * of this implementation requires prior written permission from the owner.
 */

package netchat.integrations;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PostgresArchiveService {
    private final IntegrationConfig config;

    public PostgresArchiveService(IntegrationConfig config) {
        this.config = config;
    }

    public boolean isEnabled() {
        return !config.getPostgresUrl().isBlank();
    }

    public void ensureSchema() {
        if (!isEnabled()) {
            return;
        }
        String sql = """
                create table if not exists netchat_external_archive (
                    id bigserial primary key,
                    category varchar(64) not null,
                    reference_id varchar(128) not null,
                    payload text not null,
                    created_at timestamp default current_timestamp
                )
                """;
        try (Connection connection = open()) {
            connection.createStatement().execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("PostgreSQL schema initialization failed.", exception);
        }
    }

    public void archive(String category, String referenceId, String payload) {
        if (!isEnabled()) {
            return;
        }
        String sql = "insert into netchat_external_archive(category, reference_id, payload) values (?, ?, ?)";
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category);
            statement.setString(2, referenceId);
            statement.setString(3, payload);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("PostgreSQL archive write failed.", exception);
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(config.getPostgresUrl(), config.getPostgresUser(), config.getPostgresPassword());
    }
}
