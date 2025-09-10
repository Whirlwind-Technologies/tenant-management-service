package com.nnipa.tenant.enums;

public enum IsolationStrategy {
    DATABASE_PER_TENANT,
    SCHEMA_PER_TENANT,
    SHARED_SCHEMA_ROW_LEVEL,
    SHARED_SCHEMA_BASIC,
    HYBRID_POOL
}