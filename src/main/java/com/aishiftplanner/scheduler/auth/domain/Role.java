package com.aishiftplanner.scheduler.auth.domain;

/**
 * Authorization roles, ordered from least to most privileged.
 *
 * <p>Every one of these is enforced server-side (see the {@code auth} module's method
 * security). The frontend may hide buttons based on roles, but it is never the only place a
 * permission is checked — including for AI chat, where authorization runs before a tool
 * executes rather than being requested of the model in a prompt.
 */
public enum Role {

    /** Sees and edits only their own availability, comments and published shifts. */
    EMPLOYEE,

    /** Plans and edits schedules for the locations they are assigned to. */
    SHIFT_MANAGER,

    /** Full control over one or more locations, including staffing requirements and deadlines. */
    LOCATION_MANAGER,

    /** Full control over everything inside one organization, including users and skills. */
    ORG_ADMIN,

    /** Platform operator across organizations. Not used by tenants themselves. */
    SUPER_ADMIN;

    /** Spring Security convention: authorities are the role name prefixed with {@code ROLE_}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
