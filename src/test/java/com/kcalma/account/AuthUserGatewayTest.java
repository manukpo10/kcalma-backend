package com.kcalma.account;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link AuthUserGateway}, with a mocked {@link EntityManager} — no real database
 * needed to prove both branches of "only touch auth.users when it exists" (see {@code
 * com.kcalma.integration.DatabaseIntegrationTest} for the real-Postgres, table-actually-exists
 * proof of the same class).
 */
class AuthUserGatewayTest {

    private static final String EXISTS_QUERY =
            "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'auth' AND table_name = 'users')";

    private static final String DELETE_QUERY = "DELETE FROM auth.users WHERE id = :userId";

    @Test
    void deleteIfPresent_tableExists_deletesTheRowByParameterizedId() {
        UUID userId = UUID.randomUUID();
        EntityManager entityManager = mock(EntityManager.class);
        Query existsQuery = mock(Query.class);
        Query deleteQuery = mock(Query.class);
        when(entityManager.createNativeQuery(EXISTS_QUERY)).thenReturn(existsQuery);
        when(existsQuery.getSingleResult()).thenReturn(Boolean.TRUE);
        when(entityManager.createNativeQuery(DELETE_QUERY)).thenReturn(deleteQuery);
        when(deleteQuery.setParameter("userId", userId)).thenReturn(deleteQuery);

        new AuthUserGateway(entityManager).deleteIfPresent(userId);

        verify(deleteQuery).setParameter("userId", userId);
        verify(deleteQuery).executeUpdate();
    }

    @Test
    void deleteIfPresent_tableAbsent_neverIssuesTheDeleteAndDoesNotThrow() {
        UUID userId = UUID.randomUUID();
        EntityManager entityManager = mock(EntityManager.class);
        Query existsQuery = mock(Query.class);
        when(entityManager.createNativeQuery(EXISTS_QUERY)).thenReturn(existsQuery);
        when(existsQuery.getSingleResult()).thenReturn(Boolean.FALSE);

        new AuthUserGateway(entityManager).deleteIfPresent(userId);

        verify(entityManager, never()).createNativeQuery(DELETE_QUERY);
    }
}
