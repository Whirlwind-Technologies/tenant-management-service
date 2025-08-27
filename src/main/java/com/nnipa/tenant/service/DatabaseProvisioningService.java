package com.nnipa.tenant.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for provisioning and managing tenant databases.
 * Handles database creation for DATABASE_PER_TENANT isolation strategy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseProvisioningService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/postgres}")
    private String defaultDatabaseUrl;

    @Value("${spring.datasource.username:tenant_user}")
    private String adminUsername;

    @Value("${spring.datasource.password:tenant_pass}")
    private String adminPassword;

    @Value("${app.tenant.database.host:localhost}")
    private String databaseHost;

    @Value("${app.tenant.database.port:5432}")
    private int databasePort;

    @Value("${app.encryption.key:DefaultEncryptionKey123}")
    private String encryptionKey;

    // Add admin database configuration
    @Value("${app.tenant.admin-datasource.url:jdbc:postgresql://localhost:5432/postgres}")
    private String adminDatabaseUrl;

    @Value("${app.tenant.admin-datasource.username:postgres}")
    private String adminDbUsername;

    @Value("${app.tenant.admin-datasource.password:postgres}")
    private String adminDbPassword;

    // Cache of tenant data sources
    private final Map<String, HikariDataSource> tenantDataSources = new ConcurrentHashMap<>();

    /**
     * Creates a new database for a tenant with DATABASE_PER_TENANT isolation.
     */
    public void createDatabase(String databaseName, UUID tenantId) {
        log.info("Creating database: {} for tenant: {}", databaseName, tenantId);

        try {
            validateDatabaseName(databaseName);

            // Check if database already exists using the admin connection
            if (databaseExistsViaAdmin(databaseName)) {
                log.warn("Database {} already exists, skipping creation", databaseName);
                return;
            }

            // Create database using admin connection
            createDatabaseWithAdminConnection(databaseName);

            // Initialize database with extensions and base schema
            initializeDatabase(databaseName);

            // Create tenant-specific user if needed
            createTenantDatabaseUser(databaseName, tenantId);

            // Register data source for the new database
            registerTenantDataSource(tenantId.toString(), databaseName);

            log.info("Database {} created and initialized successfully", databaseName);

        } catch (Exception e) {
            log.error("Failed to create database: {}", databaseName, e);
            throw new DatabaseProvisioningException("Failed to create database: " + databaseName, e);
        }
    }

    /**
     * Creates database using admin connection.
     * Fixed to use proper admin database URL from configuration
     */
    private void createDatabaseWithAdminConnection(String databaseName) {
        log.debug("Creating database with admin connection: {}", databaseName);

        // Use the configured admin database URL (connects to postgres database)
        String postgresUrl = extractPostgresUrl(adminDatabaseUrl);

        log.debug("Using admin connection URL: {}", postgresUrl);

        Connection conn = null;
        Statement stmt = null;

        try {
            // Use DriverManager directly for simple admin operations
            Properties props = new Properties();
            props.setProperty("user", adminDbUsername);
            props.setProperty("password", adminDbPassword);

            conn = DriverManager.getConnection(postgresUrl, props);
            conn.setAutoCommit(true);

            stmt = conn.createStatement();

            // Create database with proper settings
            String createDbSql = String.format("""
                CREATE DATABASE %s
                WITH 
                OWNER = %s
                ENCODING = 'UTF8'
                LC_COLLATE = 'en_US.UTF-8'
                LC_CTYPE = 'en_US.UTF-8'
                TEMPLATE = template0
                CONNECTION LIMIT = -1
                """, sanitizeIdentifier(databaseName), adminDbUsername);

            stmt.execute(createDbSql);
            log.info("Database created successfully: {}", databaseName);

        } catch (Exception e) {
            log.error("Failed to create database: {}", databaseName, e);
            throw new DatabaseProvisioningException("Failed to create database: " + databaseName, e);
        } finally {
            // Clean up resources
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (Exception e) {
                    log.warn("Failed to close statement", e);
                }
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception e) {
                    log.warn("Failed to close connection", e);
                }
            }
        }
    }

    /**
     * Extracts the postgres database URL from any JDBC URL
     */
    private String extractPostgresUrl(String jdbcUrl) {
        // Extract base URL (protocol, host, port)
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("Invalid PostgreSQL JDBC URL: " + jdbcUrl);
        }

        // Find the database name part (after the last /)
        int lastSlashIndex = jdbcUrl.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            throw new IllegalArgumentException("Invalid PostgreSQL JDBC URL format: " + jdbcUrl);
        }

        // Get the base URL without database name
        String baseUrl = jdbcUrl.substring(0, lastSlashIndex);

        // Check if there are query parameters
        int queryParamIndex = jdbcUrl.indexOf('?', lastSlashIndex);
        String queryParams = "";
        if (queryParamIndex != -1) {
            queryParams = jdbcUrl.substring(queryParamIndex);
        }

        // Return URL pointing to postgres database
        return baseUrl + "/postgres" + queryParams;
    }

    /**
     * Checks if database exists using admin connection
     */
    private boolean databaseExistsViaAdmin(String databaseName) {
        String postgresUrl = extractPostgresUrl(adminDatabaseUrl);

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            Properties props = new Properties();
            props.setProperty("user", adminDbUsername);
            props.setProperty("password", adminDbPassword);

            conn = DriverManager.getConnection(postgresUrl, props);
            stmt = conn.createStatement();

            String checkSql = String.format(
                    "SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = '%s')",
                    databaseName.toLowerCase()
            );

            rs = stmt.executeQuery(checkSql);
            if (rs.next()) {
                return rs.getBoolean(1);
            }
            return false;
        } catch (Exception e) {
            log.error("Error checking database existence: {}", databaseName, e);
            return false;
        } finally {
            // Clean up resources
            if (rs != null) {
                try { rs.close(); } catch (Exception e) { /* ignore */ }
            }
            if (stmt != null) {
                try { stmt.close(); } catch (Exception e) { /* ignore */ }
            }
            if (conn != null) {
                try { conn.close(); } catch (Exception e) { /* ignore */ }
            }
        }
    }

    /**
     * Checks if a database exists (fallback method using JdbcTemplate).
     * This uses the main application database connection.
     */
    public boolean databaseExists(String databaseName) {
        try {
            String sql = "SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = ?)";
            Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, databaseName.toLowerCase());
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Error checking database existence via JdbcTemplate, trying admin connection: {}", e.getMessage());
            return databaseExistsViaAdmin(databaseName);
        }
    }

    /**
     * Initializes the newly created database with extensions and schema.
     */
    private void initializeDatabase(String databaseName) {
        log.debug("Initializing database: {}", databaseName);

        String dbUrl = buildDatabaseUrl(databaseName);

        Connection conn = null;
        Statement stmt = null;

        try {
            Properties props = new Properties();
            props.setProperty("user", adminDbUsername);
            props.setProperty("password", adminDbPassword);

            conn = DriverManager.getConnection(dbUrl, props);
            conn.setAutoCommit(true);

            stmt = conn.createStatement();

            // Enable required extensions
            stmt.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
            stmt.execute("CREATE EXTENSION IF NOT EXISTS \"pgcrypto\"");
            stmt.execute("CREATE EXTENSION IF NOT EXISTS \"tablefunc\"");

            // Create application schema
            stmt.execute("CREATE SCHEMA IF NOT EXISTS app");

            // Create base tables in app schema
            createBaseTables(stmt);

            log.debug("Database initialized: {}", databaseName);

        } catch (Exception e) {
            log.error("Failed to initialize database: {}", databaseName, e);
            throw new DatabaseProvisioningException("Failed to initialize database: " + databaseName, e);
        } finally {
            if (stmt != null) {
                try { stmt.close(); } catch (Exception e) { /* ignore */ }
            }
            if (conn != null) {
                try { conn.close(); } catch (Exception e) { /* ignore */ }
            }
        }
    }

    /**
     * Creates base tables in the tenant database.
     */
    private void createBaseTables(Statement stmt) throws Exception {
        // Create audit table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS app.audit_log (
                id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                entity_type VARCHAR(100),
                entity_id VARCHAR(255),
                action VARCHAR(50),
                performed_by VARCHAR(255),
                performed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                old_values JSONB,
                new_values JSONB,
                metadata JSONB
            )
            """);

        // Create settings table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS app.tenant_settings (
                id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                key VARCHAR(255) UNIQUE NOT NULL,
                value TEXT,
                type VARCHAR(50),
                description TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);

        // Add indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_entity ON app.audit_log(entity_type, entity_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_performed_at ON app.audit_log(performed_at)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_settings_key ON app.tenant_settings(key)");

        log.debug("Base tables created successfully");
    }

    /**
     * Creates a tenant-specific database user.
     */
    private void createTenantDatabaseUser(String databaseName, UUID tenantId) {
        log.debug("Creating database user for tenant: {}", tenantId);

        String username = "tenant_" + tenantId.toString().replace("-", "").substring(0, 12);
        String password = generateSecurePassword();

        String postgresUrl = extractPostgresUrl(adminDatabaseUrl);

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            Properties props = new Properties();
            props.setProperty("user", adminDbUsername);
            props.setProperty("password", adminDbPassword);

            conn = DriverManager.getConnection(postgresUrl, props);
            conn.setAutoCommit(true);
            stmt = conn.createStatement();

            // Check if user exists
            String checkUserSql = String.format(
                    "SELECT 1 FROM pg_user WHERE usename = '%s'",
                    username
            );

            rs = stmt.executeQuery(checkUserSql);
            if (!rs.next()) {
                // Create user
                String createUserSql = String.format(
                        "CREATE USER %s WITH PASSWORD '%s'",
                        sanitizeIdentifier(username),
                        password
                );
                stmt.execute(createUserSql);
            }
            rs.close();

            // Grant privileges
            String grantSql = String.format(
                    "GRANT ALL PRIVILEGES ON DATABASE %s TO %s",
                    sanitizeIdentifier(databaseName),
                    sanitizeIdentifier(username)
            );
            stmt.execute(grantSql);

            log.debug("Database user created: {}", username);

            // Store encrypted password for future use
            storeEncryptedCredentials(tenantId, username, password);

        } catch (Exception e) {
            log.error("Failed to create database user for tenant: {}", tenantId, e);
            // Continue without failing - use admin credentials
        } finally {
            if (rs != null) {
                try { rs.close(); } catch (Exception e) { /* ignore */ }
            }
            if (stmt != null) {
                try { stmt.close(); } catch (Exception e) { /* ignore */ }
            }
            if (conn != null) {
                try { conn.close(); } catch (Exception e) { /* ignore */ }
            }
        }
    }

    /**
     * Stores encrypted database credentials for a tenant.
     */
    private void storeEncryptedCredentials(UUID tenantId, String username, String password) {
        // Implementation would store these securely, possibly in a separate credentials table
        // For now, just log that we would store them
        log.debug("Would store encrypted credentials for tenant: {}", tenantId);
    }

    /**
     * Drops a database.
     */
    public void dropDatabase(String databaseName) {
        log.warn("Dropping database: {}", databaseName);

        try {
            validateDatabaseName(databaseName);

            if (!databaseExistsViaAdmin(databaseName)) {
                log.warn("Database {} does not exist, skipping deletion", databaseName);
                return;
            }

            // Close all connections to the database
            terminateDatabaseConnections(databaseName);

            // Remove from cache
            removeTenantDataSource(databaseName);

            String postgresUrl = extractPostgresUrl(adminDatabaseUrl);

            Connection conn = null;
            Statement stmt = null;

            try {
                Properties props = new Properties();
                props.setProperty("user", adminDbUsername);
                props.setProperty("password", adminDbPassword);

                conn = DriverManager.getConnection(postgresUrl, props);
                conn.setAutoCommit(true);
                stmt = conn.createStatement();

                stmt.execute("DROP DATABASE IF EXISTS " + sanitizeIdentifier(databaseName));
                log.info("Database {} dropped successfully", databaseName);

            } finally {
                if (stmt != null) {
                    try { stmt.close(); } catch (Exception e) { /* ignore */ }
                }
                if (conn != null) {
                    try { conn.close(); } catch (Exception e) { /* ignore */ }
                }
            }

        } catch (Exception e) {
            log.error("Failed to drop database: {}", databaseName, e);
            throw new DatabaseProvisioningException("Failed to drop database: " + databaseName, e);
        }
    }

    /**
     * Terminates all connections to a database.
     */
    private void terminateDatabaseConnections(String databaseName) {
        String postgresUrl = extractPostgresUrl(adminDatabaseUrl);

        Connection conn = null;
        Statement stmt = null;

        try {
            Properties props = new Properties();
            props.setProperty("user", adminDbUsername);
            props.setProperty("password", adminDbPassword);

            conn = DriverManager.getConnection(postgresUrl, props);
            stmt = conn.createStatement();

            String sql = String.format("""
                SELECT pg_terminate_backend(pg_stat_activity.pid)
                FROM pg_stat_activity
                WHERE pg_stat_activity.datname = '%s'
                AND pid <> pg_backend_pid()
                """, databaseName);

            stmt.execute(sql);
            log.debug("Terminated connections to database: {}", databaseName);

        } catch (Exception e) {
            log.warn("Failed to terminate connections to database: {}", databaseName, e);
        } finally {
            if (stmt != null) {
                try { stmt.close(); } catch (Exception e) { /* ignore */ }
            }
            if (conn != null) {
                try { conn.close(); } catch (Exception e) { /* ignore */ }
            }
        }
    }

    /**
     * Registers a data source for a tenant database.
     */
    public HikariDataSource registerTenantDataSource(String tenantId, String databaseName) {
        log.debug("Registering data source for tenant: {}", tenantId);

        try {
            // Check if already exists
            if (tenantDataSources.containsKey(tenantId)) {
                return tenantDataSources.get(tenantId);
            }

            // Create new data source
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(buildDatabaseUrl(databaseName));
            config.setUsername(adminDbUsername);
            config.setPassword(adminDbPassword);
            config.setDriverClassName("org.postgresql.Driver");

            // Connection pool settings
            config.setMinimumIdle(2);
            config.setMaximumPoolSize(10);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setAutoCommit(true);

            // Performance settings
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            // Set pool name
            config.setPoolName("TenantPool-" + tenantId);

            HikariDataSource dataSource = new HikariDataSource(config);
            tenantDataSources.put(tenantId, dataSource);

            log.info("Data source registered for tenant: {}", tenantId);
            return dataSource;

        } catch (Exception e) {
            log.error("Failed to register data source for tenant: {}", tenantId, e);
            throw new DatabaseProvisioningException("Failed to register data source", e);
        }
    }

    /**
     * Gets a data source for a tenant.
     */
    public DataSource getTenantDataSource(String tenantId) {
        return tenantDataSources.get(tenantId);
    }

    /**
     * Removes a tenant data source from cache.
     */
    public void removeTenantDataSource(String tenantId) {
        HikariDataSource dataSource = tenantDataSources.remove(tenantId);
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.debug("Data source removed for tenant: {}", tenantId);
        }
    }

//    /**
//     * Creates a connection with the given URL and credentials.
//     */
//    private Connection createConnection(String url, String username, String password) throws Exception {
//        HikariConfig config = new HikariConfig();
//        config.setJdbcUrl(url);
//        config.setUsername(username);
//        config.setPassword(password);
//        config.setMinimumIdle(1);
//        config.setMaximumPoolSize(1);
//        config.setConnectionTimeout(5000);
//
//        try (HikariDataSource ds = new HikariDataSource(config)) {
//            return ds.getConnection();
//        }
//    }

    /**
     * Creates an admin connection.
     */
    private Connection createAdminConnection(String url) throws Exception {
        Properties props = new Properties();
        props.setProperty("user", adminDbUsername);
        props.setProperty("password", adminDbPassword);
        return DriverManager.getConnection(url, props);
    }

    /**
     * Builds database URL.
     */
    private String buildDatabaseUrl(String databaseName) {
        return String.format("jdbc:postgresql://%s:%d/%s",
                databaseHost, databasePort, databaseName);
    }

    /**
     * Validates database name to prevent SQL injection.
     */
    private void validateDatabaseName(String databaseName) {
        if (databaseName == null || databaseName.trim().isEmpty()) {
            throw new IllegalArgumentException("Database name cannot be null or empty");
        }

        // Only allow alphanumeric, underscore
        if (!databaseName.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid database name: " + databaseName);
        }

        // Check length
        if (databaseName.length() > 63) { // PostgreSQL identifier limit
            throw new IllegalArgumentException("Database name too long: " + databaseName);
        }

        // Prevent reserved words
        if (isReservedWord(databaseName)) {
            throw new IllegalArgumentException("Database name is a reserved word: " + databaseName);
        }
    }

    /**
     * Checks if a name is a PostgreSQL reserved word.
     */
    private boolean isReservedWord(String name) {
        String[] reservedWords = {
                "postgres", "template0", "template1", "public",
                "user", "role", "database", "schema", "table"
        };

        String lowerName = name.toLowerCase();
        for (String reserved : reservedWords) {
            if (reserved.equals(lowerName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sanitizes an identifier to prevent SQL injection.
     */
    private String sanitizeIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "") + "\"";
    }

    /**
     * Generates a secure password for tenant database user.
     */
    private String generateSecurePassword() {
        return UUID.randomUUID().toString().replace("-", "") + "Db!";
    }

    /**
     * Encrypts a password for storage.
     */
    public String encryptPassword(String password) {
        try {
            SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(password.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("Failed to encrypt password", e);
            throw new DatabaseProvisioningException("Failed to encrypt password", e);
        }
    }

    /**
     * Gets database statistics for a tenant database.
     */
    public DatabaseStatistics getDatabaseStatistics(String databaseName) {
        try {
            String sql = """
                SELECT 
                    pg_database_size(?) as size_bytes,
                    (SELECT count(*) FROM pg_stat_activity WHERE datname = ?) as active_connections,
                    (SELECT count(*) FROM information_schema.tables 
                     WHERE table_catalog = ? AND table_schema NOT IN ('pg_catalog', 'information_schema')) as table_count
                """;

            return jdbcTemplate.queryForObject(sql,
                    (rs, rowNum) -> DatabaseStatistics.builder()
                            .databaseName(databaseName)
                            .sizeBytes(rs.getLong("size_bytes"))
                            .activeConnections(rs.getInt("active_connections"))
                            .tableCount(rs.getInt("table_count"))
                            .build(),
                    databaseName, databaseName, databaseName);

        } catch (Exception e) {
            log.error("Failed to get database statistics: {}", databaseName, e);
            return DatabaseStatistics.builder()
                    .databaseName(databaseName)
                    .sizeBytes(0L)
                    .activeConnections(0)
                    .tableCount(0)
                    .build();
        }
    }

    // Nested exception class
    public static class DatabaseProvisioningException extends RuntimeException {
        public DatabaseProvisioningException(String message) {
            super(message);
        }

        public DatabaseProvisioningException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // Database statistics builder
    public static class DatabaseStatistics {
        private String databaseName;
        private Long sizeBytes;
        private Integer activeConnections;
        private Integer tableCount;

        public static DatabaseStatisticsBuilder builder() {
            return new DatabaseStatisticsBuilder();
        }

        public static class DatabaseStatisticsBuilder {
            private String databaseName;
            private Long sizeBytes;
            private Integer activeConnections;
            private Integer tableCount;

            public DatabaseStatisticsBuilder databaseName(String databaseName) {
                this.databaseName = databaseName;
                return this;
            }

            public DatabaseStatisticsBuilder sizeBytes(Long sizeBytes) {
                this.sizeBytes = sizeBytes;
                return this;
            }

            public DatabaseStatisticsBuilder activeConnections(Integer activeConnections) {
                this.activeConnections = activeConnections;
                return this;
            }

            public DatabaseStatisticsBuilder tableCount(Integer tableCount) {
                this.tableCount = tableCount;
                return this;
            }

            public DatabaseStatistics build() {
                DatabaseStatistics stats = new DatabaseStatistics();
                stats.databaseName = this.databaseName;
                stats.sizeBytes = this.sizeBytes;
                stats.activeConnections = this.activeConnections;
                stats.tableCount = this.tableCount;
                return stats;
            }
        }
    }
}