package com.aishiftplanner.scheduler.schedule.domain;

public enum ScheduleStatus {
    /** A solver proposal or a work-in-progress manual edit; invisible to employees. */
    DRAFT,
    /** Selected by a manager and being finalized. */
    PLANNED,
    /** Live and visible to employees. */
    PUBLISHED,
    /** Historical. */
    ARCHIVED
}
