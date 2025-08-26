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
import java.sql.Statement;
import java.util.Base64;
import java.util.Map;
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

    // Cache of tenant data sources
    private final Map<String, HikariDataSource> tenantDataSources = new ConcurrentHashMap<>();

    /**
     * Creates a new database for a tenant with DATABASE_PER_TENANT isolation.
     */
    public void createDatabase(String databaseName, UUID tenantId) {
        log.info("Creating database: {} for tenant: {}", databaseName, tenantId);

        try {
            validateDatabaseName(databaseName);

            // Check if database already exists
            if (databaseExists(databaseName)) {
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
     */
    private void createDatabaseWithAdminConnection(String databaseName) {
        log.debug("Creating database with admin connection: {}", databaseName);

        // Connect to postgres database to create new database
        String postgresUrl = defaultDatabaseUrl.replace("/tenant_db", "/postgres")
                .replace("/tenant_db_dev", "/postgres");

        try (Connection conn = createAdminConnection(postgresUrl)) {
            Statement stmt = conn.createStatement();

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
                """, sanitizeIdentifier(databaseName), adminUsername);

            stmt.execute(createDbSql);
            log.debug("Database created: {}", databaseName);

        } catch (Exception e) {
            log.error("Failed to create database: {}", databaseName, e);
            throw new DatabaseProvisioningException("Failed to create database: " + databaseName, e);
        }
    }

    /**
     * Initializes the newly created database with extensions and schema.
     */
    private void initializeDatabase(String databaseName) {
        log.debug("Initializing database: {}", databaseName);

        String dbUrl = buildDatabaseUrl(databaseName);

        try (Connection conn = createConnection(dbUrl, adminUsername, adminPassword)) {
            Statement stmt = conn.createStatement();

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
        }
    }

    /**
     * Creates base tables in the tenant database.
     */
    private void createBaseTables(Statement stmt) throws Exception {
        // Create datasets table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS app.datasets (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                name VARCHAR(255) NOT NULL,
                description TEXT,
                data_type VARCHAR(50),
                source VARCHAR(255),
                size_bytes BIGINT,
                row_count BIGINT,
                column_count INTEGER,
                metadata JSONB,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP,
                created_by VARCHAR(255),
                is_public BOOLEAN DEFAULT FALSE,
                CONSTRAINT uk_dataset_name UNIQUE (name)
            )
            """);

        // Create statistical_analyses table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS app.statistical_analyses (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                dataset_id UUID REFERENCES app.datasets(id) ON DELETE CASCADE,
                analysis_type VARCHAR(100) NOT NULL,
                analysis_name VARCHAR(255),
                description TEXT,
                parameters JSONB,
                results JSONB,
                status VARCHAR(30),
                started_at TIMESTAMP,
                completed_at TIMESTAMP,
                error_message TEXT,
                execution_time_ms BIGINT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                created_by VARCHAR(255),
                CONSTRAINT uk_analysis_name UNIQUE (analysis_name)
            )
            """);

        // Create reports table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS app.reports (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                analysis_id UUID REFERENCES app.statistical_analyses(id) ON DELETE CASCADE,
                report_name VARCHAR(255) NOT NULL,
                report_type VARCHAR(50),
                format VARCHAR(20),
                content TEXT,
                file_path VARCHAR(500),
                metadata JSONB,
                generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                generated_by VARCHAR(255),
                is_published BOOLEAN DEFAULT FALSE,
                published_at TIMESTAMP,
                access_count INTEGER DEFAULT 0,
                CONSTRAINT uk_report_name UNIQUE (report_name)
            )
            """);

        // Create indexes
        stmt.execute("CREATE INDEX idx_datasets_created_at ON app.datasets(created_at DESC)");
        stmt.execute("CREATE INDEX idx_analyses_dataset_id ON app.statistical_analyses(dataset_id)");
        stmt.execute("CREATE INDEX idx_analyses_status ON app.statistical_analyses(status)");
        stmt.execute("CREATE INDEX idx_reports_analysis_id ON app.reports(analysis_id)");
    }

    /**
     * Creates a database user specific to the tenant.
     */
    private void createTenantDatabaseUser(String databaseName, UUID tenantId) {
        log.debug("Creating database user for tenant: {}", tenantId);

        String username = "tenant_" + tenantId.toString().substring(0, 8);
        String password = generateSecurePassword();

        try (Connection conn = createAdminConnection(buildDatabaseUrl(databaseName))) {
            Statement stmt = conn.createStatement();

            // Create user if not exists
            stmt.execute(String.format(
                    "DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_user WHERE usename = '%s') THEN " +
                            "CREATE USER %s WITH PASSWORD '%s'; END IF; END $$;",
                    username, username, password));

            // Grant connect on database
            stmt.execute(String.format("GRANT CONNECT ON DATABASE %s TO %s",
                    sanitizeIdentifier(databaseName), username));

            // Grant usage on app schema
            stmt.execute(String.format("GRANT USAGE ON SCHEMA app TO %s", username));

            // Grant all privileges on all tables in app schema
            stmt.execute(String.format("GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA app TO %s", username));
            stmt.execute(String.format("GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA app TO %s", username));

            // Set default privileges for future objects
            stmt.execute(String.format(
                    "ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT ALL ON TABLES TO %s", username));
            stmt.execute(String.format(
                    "ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT ALL ON SEQUENCES TO %s", username));

            log.debug("Database user created: {}", username);

            // Store encrypted password in tenant record (would be done by caller)
            // This is just for demonstration - actual implementation would update tenant record

        } catch (Exception e) {
            log.error("Failed to create database user for tenant: {}", tenantId, e);
            // Non-critical, continue
        }
    }

    /**
     * Drops a database.
     */
    public void dropDatabase(String databaseName) {
        log.warn("Dropping database: {}", databaseName);

        try {
            validateDatabaseName(databaseName);

            if (!databaseExists(databaseName)) {
                log.warn("Database {} does not exist, skipping deletion", databaseName);
                return;
            }

            // Close all connections to the database
            terminateDatabaseConnections(databaseName);

            // Remove from cache
            removeTenantDataSource(databaseName);

            // Drop database
            try (Connection conn = createAdminConnection(defaultDatabaseUrl.replace("/tenant_db", "/postgres"))) {
                Statement stmt = conn.createStatement();
                stmt.execute("DROP DATABASE IF EXISTS " + sanitizeIdentifier(databaseName));
                log.info("Database {} dropped successfully", databaseName);
            }

        } catch (Exception e) {
            log.error("Failed to drop database: {}", databaseName, e);
            throw new DatabaseProvisioningException("Failed to drop database: " + databaseName, e);
        }
    }

    /**
     * Checks if a database exists.
     */
    public boolean databaseExists(String databaseName) {
        try {
            String sql = "SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = ?)";
            Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, databaseName.toLowerCase());
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Error checking database existence: {}", databaseName, e);
            return false;
        }
    }

    /**
     * Terminates all connections to a database.
     */
    private void terminateDatabaseConnections(String databaseName) {
        try {
            String sql = String.format("""
                SELECT pg_terminate_backend(pg_stat_activity.pid)
                FROM pg_stat_activity
                WHERE pg_stat_activity.datname = '%s'
                AND pid <> pg_backend_pid()
                """, databaseName);

            jdbcTemplate.execute(sql);
            log.debug("Terminated connections to database: {}", databaseName);

        } catch (Exception e) {
            log.warn("Failed to terminate connections to database: {}", databaseName, e);
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
            config.setUsername(adminUsername);
            config.setPassword(adminPassword);
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

    /**
     * Creates a connection with the given URL and credentials.
     */
    private Connection createConnection(String url, String username, String password) throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(5000);

        try (HikariDataSource ds = new HikariDataSource(config)) {
            return ds.getConnection();
        }
    }

    /**
     * Creates an admin connection.
     */
    private Connection createAdminConnection(String url) throws Exception {
        return createConnection(url, adminUsername, adminPassword);
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
     * Decrypts a password.
     */
    public String decryptPassword(String encryptedPassword) {
        try {
            SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedPassword));
            return new String(decrypted);
        } catch (Exception e) {
            log.error("Failed to decrypt password", e);
            throw new DatabaseProvisioningException("Failed to decrypt password", e);
        }
    }

    /**
     * Database statistics DTO.
     */
    @lombok.Builder
    @lombok.Data
    public static class DatabaseStatistics {
        private String databaseName;
        private long sizeBytes;
        private int activeConnections;
        private int tableCount;
    }

    /**
     * Custom exception for database provisioning operations.
     */
    public static class DatabaseProvisioningException extends RuntimeException {
        public DatabaseProvisioningException(String message) {
            super(message);
        }

        public DatabaseProvisioningException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}