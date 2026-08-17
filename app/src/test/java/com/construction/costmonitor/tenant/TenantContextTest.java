package com.construction.costmonitor.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void shouldSetAndGetCompanyId() {
        TenantContext.setCompanyId(42L);
        assertEquals(42L, TenantContext.getCompanyId());
        assertEquals(42L, TenantContext.requireCompanyId());
    }

    @Test
    void requireShouldFailWhenNotSet() {
        assertThrows(IllegalStateException.class, TenantContext::requireCompanyId);
    }

    @Test
    void clearShouldRemoveValue() {
        TenantContext.setCompanyId(1L);
        TenantContext.clear();
        assertNull(TenantContext.getCompanyId());
    }
}
