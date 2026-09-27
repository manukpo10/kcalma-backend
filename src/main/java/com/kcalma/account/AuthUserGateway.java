package com.kcalma.account;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Best-effort bridge to Supabase's {@code auth.users} table, which this app's JPA model
 * deliberately never maps — it lives in a schema Supabase owns and manages, entirely outside this
 * app's own Flyway migrations (see {@code V1__baseline.sql}'s schema-scoped design).
 *
 * <p>Production (Supabase) always has {@code auth.users}. A local Postgres or a Testcontainers
 * database started from this app's own migrations never does — that is a normal, fully supported
 * case, not a misconfiguration, so {@link #deleteIfPresent} checks first and quietly no-ops (with
 * a log line) rather than failing.
 */
@Component
class AuthUserGateway {

    private static final Logger log = LoggerFactory.getLogger(AuthUserGateway.class);

    private static final String EXISTS_QUERY =
            "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'auth' AND table_name = 'users')";

    private static final String DELETE_QUERY = "DELETE FROM auth.users WHERE id = :userId";

    private final EntityManager entityManager;

    AuthUserGateway(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Deletes {@code userId}'s row from {@code auth.users} if that table exists in this database;
     * otherwise logs and does nothing. Participates in the caller's transaction (see {@code
     * AccountService#deleteAccount}) — never opens one of its own.
     */
    void deleteIfPresent(UUID userId) {
        if (!authUsersTableExists()) {
            log.info("auth.users table not found -- skipping Supabase auth user deletion for {}", userId);
            return;
        }
        entityManager.createNativeQuery(DELETE_QUERY).setParameter("userId", userId).executeUpdate();
    }

    private boolean authUsersTableExists() {
        Object result = entityManager.createNativeQuery(EXISTS_QUERY).getSingleResult();
        return Boolean.TRUE.equals(result);
    }
}
