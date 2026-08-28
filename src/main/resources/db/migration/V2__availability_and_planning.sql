-- =============================================================================
-- V2: Planning periods, availability, employee comments and the audit log
-- =============================================================================

-- --- Planning periods --------------------------------------------------------
-- A planning period is the unit everything else hangs off: employees submit availability
-- for it, managers define staffing for it, and the solver produces schedules for it.
CREATE TABLE planning_periods (
    id                     UUID        PRIMARY KEY,
    organization_id        UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    location_id            UUID        NOT NULL REFERENCES locations (id) ON DELETE CASCADE,
    -- Local calendar dates in the location's own timezone, inclusive on both ends.
    start_date             DATE        NOT NULL,
    end_date               DATE        NOT NULL,
    -- Deadline is an absolute instant: "Wednesday 18:00" is unambiguous only once anchored
    -- to the location's zone, and it is compared against server time in UTC.
    availability_deadline  TIMESTAMPTZ NOT NULL,
    status                 VARCHAR(30) NOT NULL,
    created_by             UUID        REFERENCES users (id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_planning_periods_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_planning_periods_status CHECK (
        status IN ('OPEN_FOR_AVAILABILITY', 'READY_FOR_PLANNING', 'PLANNING', 'DRAFT', 'PUBLISHED', 'ARCHIVED')
    ),
    -- One planning period per location per start date; re-planning the same week happens by
    -- reopening the existing period, not by creating a silent duplicate.
    CONSTRAINT uq_planning_periods_location_start UNIQUE (location_id, start_date)
);
CREATE INDEX ix_planning_periods_organization ON planning_periods (organization_id);
CREATE INDEX ix_planning_periods_location_status ON planning_periods (location_id, status);

-- --- Availability ------------------------------------------------------------
-- One row per time window. Several rows may share (employee, date) so an employee can say
-- "10:00-14:00 and 18:00-23:00" — hence no unique constraint on (employee, date).
CREATE TABLE availabilities (
    id                 UUID        PRIMARY KEY,
    organization_id    UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    planning_period_id UUID        NOT NULL REFERENCES planning_periods (id) ON DELETE CASCADE,
    employee_id        UUID        NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    date               DATE        NOT NULL,
    availability_type  VARCHAR(20) NOT NULL,
    -- NULL start/end means the type applies to the whole day (typical for UNAVAILABLE).
    start_time         TIME,
    end_time           TIME,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_availabilities_type CHECK (
        availability_type IN ('AVAILABLE', 'PREFERRED', 'UNAVAILABLE')
    ),
    CONSTRAINT ck_availabilities_times CHECK (
        (start_time IS NULL AND end_time IS NULL)
            OR (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time)
    )
);
CREATE INDEX ix_availabilities_period_employee ON availabilities (planning_period_id, employee_id);
CREATE INDEX ix_availabilities_period_date ON availabilities (planning_period_id, date);
CREATE INDEX ix_availabilities_organization ON availabilities (organization_id);

-- --- Employee comments -------------------------------------------------------
-- Free text written by employees ("Samstag kann ich, aber bitte erst ab 17 Uhr, weil ich
-- vorher Uni habe"). The ORIGINAL text is always kept verbatim: it is the authoritative
-- record of what the person actually said, and any AI interpretation of it is a derived,
-- reviewable artefact stored separately below.
CREATE TABLE employee_comments (
    id                 UUID        PRIMARY KEY,
    organization_id    UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    planning_period_id UUID        NOT NULL REFERENCES planning_periods (id) ON DELETE CASCADE,
    employee_id        UUID        NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    original_text      TEXT        NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_employee_comments_text_not_blank CHECK (length(btrim(original_text)) > 0)
);
CREATE INDEX ix_employee_comments_period_employee
    ON employee_comments (planning_period_id, employee_id);

-- --- AI interpretations of comments -----------------------------------------
-- Derived from employee_comments by the local LLM. Deliberately a separate table from the
-- comment itself, with confidence and a review flag: an uncertain interpretation must never
-- silently become a hard planning constraint (product brief §12).
CREATE TABLE comment_interpretations (
    id                    UUID           PRIMARY KEY,
    organization_id       UUID           NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    comment_id            UUID           NOT NULL REFERENCES employee_comments (id) ON DELETE CASCADE,
    interpreted_date      DATE,
    availability_type     VARCHAR(20),
    preferred_start_time  TIME,
    preferred_end_time    TIME,
    hard_constraint       BOOLEAN        NOT NULL DEFAULT FALSE,
    confidence            NUMERIC(4, 3)  NOT NULL,
    source                VARCHAR(40)    NOT NULL,
    interpretation        TEXT           NOT NULL,
    reviewed_by           UUID           REFERENCES users (id) ON DELETE SET NULL,
    review_status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version               BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_comment_interpretations_confidence CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT ck_comment_interpretations_type CHECK (
        availability_type IS NULL OR availability_type IN ('AVAILABLE', 'PREFERRED', 'UNAVAILABLE')
    ),
    CONSTRAINT ck_comment_interpretations_review CHECK (
        review_status IN ('PENDING', 'ACCEPTED', 'REJECTED')
    ),
    CONSTRAINT ck_comment_interpretations_source CHECK (
        source IN ('LOCAL_LLM', 'MANUAL', 'RULE_BASED')
    ),
    CONSTRAINT ck_comment_interpretations_times CHECK (
        preferred_end_time IS NULL OR preferred_start_time IS NULL
            OR preferred_end_time > preferred_start_time
    )
);
CREATE INDEX ix_comment_interpretations_comment ON comment_interpretations (comment_id);
CREATE INDEX ix_comment_interpretations_review ON comment_interpretations (organization_id, review_status);

-- --- Audit log ---------------------------------------------------------------
-- Append-only record of consequential actions. There is intentionally no update or delete
-- path in the application for this table.
CREATE TABLE audit_log_entries (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    actor_user_id   UUID        REFERENCES users (id) ON DELETE SET NULL,
    action          VARCHAR(60) NOT NULL,
    entity_type     VARCHAR(60) NOT NULL,
    entity_id       UUID,
    -- Free-form JSON context (what changed, from what to what). Must never contain
    -- credentials, tokens or password hashes.
    metadata        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    correlation_id  VARCHAR(64),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_log_org_time ON audit_log_entries (organization_id, occurred_at DESC);
CREATE INDEX ix_audit_log_entity ON audit_log_entries (entity_type, entity_id);
CREATE INDEX ix_audit_log_actor ON audit_log_entries (actor_user_id);
