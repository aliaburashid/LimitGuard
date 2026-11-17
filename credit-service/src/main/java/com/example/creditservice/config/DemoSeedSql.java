package com.example.creditservice.config;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;


// This class helps the demo seeders execute SQL files.
// Citi and GIB can both use it to insert their demo data.
@Component
@RequiredArgsConstructor
class DemoSeedSql {

    // JdbcTemplate allows us to execute SQL using the database connection.
    private final JdbcTemplate jdbc;


    // Executes an SQL script from the project's resources folder.
    void execute(String path) {

        // Use the current database connection and transaction.
        // This allows the SQL script to access temporary tables
        // created earlier in the same transaction.
        jdbc.execute((ConnectionCallback<Void>) connection -> {

            // Find the SQL file using its path in the resources folder.
            ClassPathResource sqlFile = new ClassPathResource(path);

            // Read the SQL file using UTF-8 encoding.
            EncodedResource resource = new EncodedResource(
                    sqlFile,
                    StandardCharsets.UTF_8
            );

            // Execute all SQL statements in the file.
            ScriptUtils.executeSqlScript(connection, resource);

            // Return null because this operation does not return a result.
            return null;
        });
    }
}
