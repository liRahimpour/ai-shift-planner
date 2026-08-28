-- =============================================================================
-- V4: Asynchronous planning jobs
-- =============================================================================

-- A solver run takes tens of seconds; an HTTP request must not. A job row is the handle a
-- client polls, and - just as importantly - the lock that stops "Plan generieren" being
-- clicked five times from starting five solver runs over the same period.
CREATE TABLE planning_jobs (
    id                 UUID        PRIMARY KEY,
    organization_id    UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    planning_period_id UUID        NOT NULL REFERENCES planning_periods (id) ON DELETE CASCADE,
    status             VARCHAR(20) NOT NULL,
    requested_by       UUID        REFERENCES users (id) ON DELETE SET NULL,
    -- Populated on failure; shown to the manager instead of a stack trace.
    failure_reason     TEXT,
    strategies         VARCHAR(200) NOT NULL DEFAULT 'FAIR,COST_OPTIMIZED,BALANCED',
    progress_note      VARCHAR(200),
    started_at         TIMESTAMPTZ,
    finished_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_planning_jobs_status CHECK (
        status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')
    )
);
CREATE INDEX ix_planning_jobs_period ON planning_jobs (planning_period_id, created_at DESC);
CREATE INDEX ix_planning_jobs_organization ON planning_jobs (organization_id);

-- At most one active job per planning period. Enforced by the database rather than by an
-- application-level check, because two concurrent requests can both pass an application
-- check before either has written its row - the classic double-submit race.
CREATE UNIQUE INDEX uq_planning_jobs_one_active_per_period
    ON planning_jobs (planning_period_id)
    WHERE status IN ('QUEUED', 'RUNNING');
