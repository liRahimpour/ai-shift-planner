-- =============================================================================
-- V3: Staffing requirements, shifts and shift assignments
-- =============================================================================

-- --- Staffing requirements ---------------------------------------------------
-- What a manager says the floor needs: "Saturday, kitchen, 16:00-23:00, 4 people, at least
-- one with CLOSING". Shifts are generated from these; the requirement stays as the record
-- of intent so a regenerated plan does not lose why a shift exists.
CREATE TABLE staffing_requirements (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    location_id     UUID        NOT NULL REFERENCES locations (id) ON DELETE CASCADE,
    department_id   UUID        NOT NULL REFERENCES departments (id) ON DELETE CASCADE,
    planning_period_id UUID     NOT NULL REFERENCES planning_periods (id) ON DELETE CASCADE,
    date            DATE        NOT NULL,
    start_time      TIME        NOT NULL,
    end_time        TIME        NOT NULL,
    -- end_time <= start_time means the block runs past midnight (e.g. bar 18:00-02:00);
    -- crosses_midnight makes that explicit rather than leaving it to be inferred.
    crosses_midnight BOOLEAN    NOT NULL DEFAULT FALSE,
    minimum_staff   INTEGER     NOT NULL,
    preferred_staff INTEGER     NOT NULL,
    maximum_staff   INTEGER     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_staffing_counts CHECK (
        minimum_staff >= 0
            AND preferred_staff >= minimum_staff
            AND maximum_staff >= preferred_staff
    ),
    CONSTRAINT ck_staffing_times CHECK (crosses_midnight OR end_time > start_time)
);
CREATE INDEX ix_staffing_requirements_period ON staffing_requirements (planning_period_id, date);
CREATE INDEX ix_staffing_requirements_department ON staffing_requirements (department_id, date);
CREATE INDEX ix_staffing_requirements_organization ON staffing_requirements (organization_id);

-- Skills a requirement demands, with how many people must hold each. "3 people, at least
-- 1x BAR and 1x CLOSING" is two rows here, not a flag on the requirement.
CREATE TABLE staffing_requirement_skills (
    requirement_id UUID    NOT NULL REFERENCES staffing_requirements (id) ON DELETE CASCADE,
    skill_id       UUID    NOT NULL REFERENCES skills (id) ON DELETE CASCADE,
    required_count INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (requirement_id, skill_id),
    CONSTRAINT ck_requirement_skill_count CHECK (required_count >= 1)
);

-- --- Shifts ------------------------------------------------------------------
-- A concrete block of work to be staffed. Generated from a staffing requirement, but
-- editable independently afterwards (a manager may split or move one shift without
-- rewriting the requirement it came from).
CREATE TABLE shifts (
    id                 UUID        PRIMARY KEY,
    organization_id    UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    location_id        UUID        NOT NULL REFERENCES locations (id) ON DELETE CASCADE,
    department_id      UUID        NOT NULL REFERENCES departments (id) ON DELETE CASCADE,
    planning_period_id UUID        NOT NULL REFERENCES planning_periods (id) ON DELETE CASCADE,
    -- Nullable: hand-created shifts have no originating requirement.
    requirement_id     UUID        REFERENCES staffing_requirements (id) ON DELETE SET NULL,
    date               DATE        NOT NULL,
    start_time         TIME        NOT NULL,
    end_time           TIME        NOT NULL,
    crosses_midnight   BOOLEAN     NOT NULL DEFAULT FALSE,
    required_employees INTEGER     NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_shifts_status CHECK (status IN ('DRAFT', 'PLANNED', 'PUBLISHED', 'LOCKED')),
    CONSTRAINT ck_shifts_required_employees CHECK (required_employees >= 0),
    CONSTRAINT ck_shifts_times CHECK (crosses_midnight OR end_time > start_time)
);
CREATE INDEX ix_shifts_period_date ON shifts (planning_period_id, date);
CREATE INDEX ix_shifts_department_date ON shifts (department_id, date);
CREATE INDEX ix_shifts_organization ON shifts (organization_id);

CREATE TABLE shift_required_skills (
    shift_id       UUID    NOT NULL REFERENCES shifts (id) ON DELETE CASCADE,
    skill_id       UUID    NOT NULL REFERENCES skills (id) ON DELETE CASCADE,
    required_count INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (shift_id, skill_id),
    CONSTRAINT ck_shift_skill_count CHECK (required_count >= 1)
);

-- --- Schedules (one per generated proposal) ----------------------------------
-- A planning run produces three schedules (FAIR / COST_OPTIMIZED / BALANCED) over the same
-- shifts. Keeping them as separate schedule rows - rather than three sets of shifts - is
-- what makes "compare the options, then pick one" a cheap operation.
CREATE TABLE schedules (
    id                       UUID           PRIMARY KEY,
    organization_id          UUID           NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    planning_period_id       UUID           NOT NULL REFERENCES planning_periods (id) ON DELETE CASCADE,
    strategy                 VARCHAR(30)    NOT NULL,
    status                   VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    selected                 BOOLEAN        NOT NULL DEFAULT FALSE,
    -- Solver output, stored so the UI can rank proposals without re-solving.
    hard_score               BIGINT         NOT NULL DEFAULT 0,
    soft_score               BIGINT         NOT NULL DEFAULT 0,
    total_staff_cost         NUMERIC(12, 2) NOT NULL DEFAULT 0,
    preference_satisfaction  NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    contract_hours_deviation NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    unfilled_positions       INTEGER        NOT NULL DEFAULT 0,
    overtime_hours           NUMERIC(7, 2)  NOT NULL DEFAULT 0,
    fairness_score           NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version                  BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_schedules_strategy CHECK (strategy IN ('FAIR', 'COST_OPTIMIZED', 'BALANCED', 'MANUAL')),
    CONSTRAINT ck_schedules_status CHECK (status IN ('DRAFT', 'PLANNED', 'PUBLISHED', 'ARCHIVED'))
);
CREATE INDEX ix_schedules_period ON schedules (planning_period_id);
-- At most one selected schedule per planning period: a partial unique index expresses
-- "only one may be true" without forbidding many unselected rows.
CREATE UNIQUE INDEX uq_schedules_one_selected_per_period
    ON schedules (planning_period_id) WHERE selected;

-- --- Shift assignments -------------------------------------------------------
CREATE TABLE shift_assignments (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    schedule_id     UUID        NOT NULL REFERENCES schedules (id) ON DELETE CASCADE,
    shift_id        UUID        NOT NULL REFERENCES shifts (id) ON DELETE CASCADE,
    -- NULL means the solver could not fill this slot: an unfilled position is data the
    -- manager must see, not a row to silently omit.
    employee_id     UUID        REFERENCES employees (id) ON DELETE SET NULL,
    -- A pinned assignment is a manager's decision and survives re-optimization untouched.
    pinned          BOOLEAN     NOT NULL DEFAULT FALSE,
    slot_index      INTEGER     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_shift_assignments_slot CHECK (slot_index >= 0),
    -- One row per (schedule, shift, slot): prevents duplicate rows for the same seat.
    CONSTRAINT uq_shift_assignments_slot UNIQUE (schedule_id, shift_id, slot_index)
);
CREATE INDEX ix_shift_assignments_schedule ON shift_assignments (schedule_id);
CREATE INDEX ix_shift_assignments_employee ON shift_assignments (employee_id);
CREATE INDEX ix_shift_assignments_shift ON shift_assignments (shift_id);
-- The same employee must not occupy two seats in one shift of one schedule.
CREATE UNIQUE INDEX uq_shift_assignments_employee_per_shift
    ON shift_assignments (schedule_id, shift_id, employee_id) WHERE employee_id IS NOT NULL;
