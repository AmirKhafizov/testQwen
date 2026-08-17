package com.construction.costmonitor.tenant;

/**
 * Holds the current company (tenant) id for the request/thread.
 * In production this will be populated from JWT / SecurityContext.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_COMPANY_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCompanyId(Long companyId) {
        CURRENT_COMPANY_ID.set(companyId);
    }

    public static Long getCompanyId() {
        return CURRENT_COMPANY_ID.get();
    }

    public static Long requireCompanyId() {
        Long id = getCompanyId();
        if (id == null) {
            throw new IllegalStateException("Tenant (companyId) is not set in current context");
        }
        return id;
    }

    public static void clear() {
        CURRENT_COMPANY_ID.remove();
    }
}
