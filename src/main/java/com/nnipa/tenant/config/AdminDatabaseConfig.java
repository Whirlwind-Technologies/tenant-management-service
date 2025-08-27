package com.nnipa.tenant.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class AdminDatabaseConfig {

    @Value("${app.tenant.admin-datasource.url}")
    private String adminDbUrl;

    @Value("${app.tenant.admin-datasource.username}")
    private String adminDbUsername;

    @Value("${app.tenant.admin-datasource.password}")
    private String adminDbPassword;

    @Value("${app.tenant.admin-datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Bean(name = "adminDataSource")
    public DataSource adminDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(adminDbUrl);
        config.setUsername(adminDbUsername);
        config.setPassword(adminDbPassword);
        config.setDriverClassName(driverClassName);
        config.setPoolName("AdminHikariPool");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setAutoCommit(true);

        // Important: Set connection properties for admin operations
        config.addDataSourceProperty("reWriteBatchedInserts", "true");
        config.addDataSourceProperty("stringtype", "unspecified");

        return new HikariDataSource(config);
    }

    @Bean(name = "adminJdbcTemplate")
    public JdbcTemplate adminJdbcTemplate() {
        return new JdbcTemplate(adminDataSource());
    }
}