package com.thomas.order_management.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Heroku DataSource Configuration
 * Konfiguriert automatisch die Datenbankverbindung über DATABASE_URL
 */
@Configuration
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "heroku")
public class HerokuDataSourceConfig {

    /**
     * Konfiguriert DataSource für Heroku PostgreSQL
     * Parst die DATABASE_URL Environment Variable automatisch
     */
    @Bean
    @Primary
    public DataSource herokuDataSource() throws URISyntaxException {
        String databaseUrl = System.getenv("DATABASE_URL");
        
        if (databaseUrl == null || databaseUrl.isEmpty()) {
            throw new IllegalStateException("DATABASE_URL environment variable is required for Heroku deployment");
        }

        URI dbUri = new URI(databaseUrl);
        
        String dbUrl = "jdbc:postgresql://" + dbUri.getHost() + ':' + dbUri.getPort() + dbUri.getPath();
        String username = dbUri.getUserInfo().split(":")[0];
        String password = dbUri.getUserInfo().split(":")[1];

        return DataSourceBuilder
                .create()
                .url(dbUrl + "?sslmode=require")  // Heroku requires SSL
                .username(username)
                .password(password)
                .driverClassName("org.postgresql.Driver")
                .build();
    }
}