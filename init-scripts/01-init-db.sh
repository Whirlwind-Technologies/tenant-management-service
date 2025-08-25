#!/bin/bash
# init-scripts/01-init-db.sh
# Database initialization script for Docker PostgreSQL container

set -e

# Wait for PostgreSQL to be ready
until pg_isready -U postgres; do
  echo "Waiting for PostgreSQL to be ready..."
  sleep 2
done

echo "PostgreSQL is ready. Starting database initialization..."

# Create databases
psql -v ON_ERROR_STOP=1 --username postgres <<-EOSQL
    -- Create development database and user
    CREATE USER tenant_dev WITH PASSWORD 'dev_password' CREATEDB CREATEROLE;
    CREATE DATABASE tenant_db_dev OWNER tenant_dev;
    GRANT ALL PRIVILEGES ON DATABASE tenant_db_dev TO tenant_dev;

    -- Create test database and user
    CREATE USER tenant_test WITH PASSWORD 'test_password';
    CREATE DATABASE tenant_db_test OWNER tenant_test;
    GRANT ALL PRIVILEGES ON DATABASE tenant_db_test TO tenant_test;

    -- Create production database and user (for local testing)
    CREATE USER tenant_user WITH PASSWORD 'tenant_pass' CREATEDB CREATEROLE REPLICATION;
    CREATE DATABASE tenant_db OWNER tenant_user;
    GRANT ALL PRIVILEGES ON DATABASE tenant_db TO tenant_user;
EOSQL

# Connect to development database and set up extensions
psql -v ON_ERROR_STOP=1 --username postgres --dbname tenant_db_dev <<-EOSQL
    -- Enable UUID extension
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

    -- Enable crypto extension for secure passwords
    CREATE EXTENSION IF NOT EXISTS "pgcrypto";

    -- Enable tablefunc for advanced queries
    CREATE EXTENSION IF NOT EXISTS "tablefunc";

    -- Grant schema creation privilege to tenant_dev
    GRANT CREATE ON DATABASE tenant_db_dev TO tenant_dev;
EOSQL

# Connect to test database and set up extensions
psql -v ON_ERROR_STOP=1 --username postgres --dbname tenant_db_test <<-EOSQL
    -- Enable UUID extension
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

    -- Enable crypto extension
    CREATE EXTENSION IF NOT EXISTS "pgcrypto";

    -- Grant necessary privileges
    GRANT CREATE ON DATABASE tenant_db_test TO tenant_test;
EOSQL

# Connect to production database and set up extensions
psql -v ON_ERROR_STOP=1 --username postgres --dbname tenant_db <<-EOSQL
    -- Enable UUID extension
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

    -- Enable crypto extension
    CREATE EXTENSION IF NOT EXISTS "pgcrypto";

    -- Enable tablefunc for advanced queries
    CREATE EXTENSION IF NOT EXISTS "tablefunc";

    -- Grant schema creation privilege to tenant_user
    GRANT CREATE ON DATABASE tenant_db TO tenant_user;
EOSQL

# Create template database for tenant isolation
psql -v ON_ERROR_STOP=1 --username postgres <<-EOSQL
    -- Create template database
    CREATE DATABASE tenant_template
        WITH OWNER = tenant_user
        ENCODING = 'UTF8'
        IS_TEMPLATE = true;
EOSQL

# Set up template database
psql -v ON_ERROR_STOP=1 --username postgres --dbname tenant_template <<-EOSQL
    -- Enable extensions in template
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
    CREATE EXTENSION IF NOT EXISTS "pgcrypto";

    -- Create app schema
    CREATE SCHEMA IF NOT EXISTS app;
    GRANT ALL ON SCHEMA app TO tenant_user;
EOSQL

echo "Database initialization completed successfully!"

# Create a marker file to indicate initialization is complete
touch /docker-entrypoint-initdb.d/.initialized

echo "==================================="
echo "Databases created:"
echo "  - tenant_db (production)"
echo "  - tenant_db_dev (development)"
echo "  - tenant_db_test (test)"
echo "  - tenant_template (template)"
echo ""
echo "Users created:"
echo "  - tenant_user (production)"
echo "  - tenant_dev (development)"
echo "  - tenant_test (test)"
echo "==================================="