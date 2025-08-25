package com.nnipa.tenant.config;

import com.nnipa.tenant.enums.TenantIsolationStrategy;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-tenant data source configuration supporting different isolation strategies.
 * Manages dynamic data source routing based on tenant context.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MultiTenantDataSourceConfig {

    private final DataSourceProperties dataSourceProperties;

    /**
     * Cache of tenant-specific data sources for DATABASE_PER_TENANT strategy.
     */
    private final Map<String, DataSource> tenantDataSources = new ConcurrentHashMap<>();

    /**
     * Primary data source bean that routes to appropriate tenant data source.
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();

        // Configure default data source (for shared schema strategies)
        DataSource defaultDataSource = createDefaultDataSource();
        routingDataSource.setDefaultTargetDataSource(defaultDataSource);

        // Initialize with empty map (will be populated dynamically)
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("default", defaultDataSource);
        routingDataSource.setTargetDataSources(targetDataSources);

        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }

    /**
     * Creates the default data source for shared schema strategies.
     */
    private DataSource createDefaultDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dataSourceProperties.getUrl());
        config.setUsername(dataSourceProperties.getUsername());
        config.setPassword(dataSourceProperties.getPassword());
        config.setDriverClassName(dataSourceProperties.getDriverClassName());

        // Connection pool settings
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("DefaultTenantPool");

        // Performance settings
        config.setAutoCommit(false);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        log.info("Created default data source for shared schema strategies");
        return new HikariDataSource(config);
    }

    /**
     * Creates a tenant-specific data source for DATABASE_PER_TENANT strategy.
     */
    public DataSource createTenantDataSource(String tenantId, String jdbcUrl,
                                             String username, String password,
                                             Integer poolSize) {
        String dataSourceKey = "tenant_" + tenantId;

        // Check if data source already exists
        if (tenantDataSources.containsKey(dataSourceKey)) {
            return tenantDataSources.get(dataSourceKey);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");

        // Tenant-specific pool settings
        config.setMaximumPoolSize(poolSize != null ? poolSize : 10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("TenantPool-" + tenantId);

        // Tenant identification
        config.addDataSourceProperty("ApplicationName", "NNIPA-Tenant-" + tenantId);

        HikariDataSource dataSource = new HikariDataSource(config);
        tenantDataSources.put(dataSourceKey, dataSource);

        // Update routing data source
        if (dataSource() instanceof TenantRoutingDataSource routingDataSource) {
            routingDataSource.addDataSource(dataSourceKey, dataSource);
        }

        log.info("Created tenant-specific data source for tenant: {}", tenantId);
        return dataSource;
    }

    /**
     * Removes a tenant data source (for cleanup or migration).
     */
    public void removeTenantDataSource(String tenantId) {
        String dataSourceKey = "tenant_" + tenantId;
        DataSource dataSource = tenantDataSources.remove(dataSourceKey);

        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
            log.info("Closed and removed data source for tenant: {}", tenantId);
        }

        if (dataSource() instanceof TenantRoutingDataSource routingDataSource) {
            routingDataSource.removeDataSource(dataSourceKey);
        }
    }

    /**
     * Custom routing data source that determines target based on tenant context.
     */
    public static class TenantRoutingDataSource extends AbstractRoutingDataSource {

        private Map<Object, Object> dataSources = new HashMap<>();

        @Override
        protected Object determineCurrentLookupKey() {
            String tenantId = TenantContext.getCurrentTenant();
            TenantIsolationStrategy strategy = TenantContext.getIsolationStrategy();

            if (tenantId == null || strategy == null) {
                return "default";
            }

            // Route based on isolation strategy
            return switch (strategy) {
                case DATABASE_PER_TENANT -> "tenant_" + tenantId;
                case SCHEMA_PER_TENANT, HYBRID_POOL -> {
                    // Use default connection but with schema context
                    TenantContext.setCurrentSchema(TenantContext.getTenantSchema());
                    yield "default";
                }
                case SHARED_SCHEMA_ROW_LEVEL, SHARED_SCHEMA_BASIC -> "default";
            };
        }

        public void addDataSource(String key, DataSource dataSource) {
            dataSources.put(key, dataSource);
            setTargetDataSources(dataSources);
            afterPropertiesSet();
        }

        public void removeDataSource(String key) {
            dataSources.remove(key);
            setTargetDataSources(dataSources);
            afterPropertiesSet();
        }
    }

    /**
     * Configuration properties for multi-tenant data sources.
     */
    @ConfigurationProperties(prefix = "app.multi-tenant")
    public static class MultiTenantProperties {
        private boolean enabled = true;
        private String defaultIsolationStrategy = "SHARED_SCHEMA_ROW_LEVEL";
        private Map<String, DataSourceConfig> tenantDataSources = new HashMap<>();

        // Getters and setters
        public static class DataSourceConfig {
            private String url;
            private String username;
            private String password;
            private Integer poolSize;
            // Getters and setters
        }
    }
}