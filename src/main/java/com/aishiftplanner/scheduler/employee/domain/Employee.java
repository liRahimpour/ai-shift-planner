package com.aishiftplanner.scheduler.employee.domain;

import com.aishiftplanner.scheduler.shared.domain.TenantScopedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A schedulable person.
 *
 * <p>Hour limits are stored per employee as plain numbers rather than being derived from
 * {@link EmploymentType}, because the legal and contractual ceilings differ by country and
 * collective agreement (see the note on {@code EmploymentType}). The solver reads these
 * numbers; it does not know or care what "Minijob" means.
 *
 * <p>Skills, departments and additional cleared locations are modelled as id sets rather
 * than JPA associations. That keeps the aggregate small and lets the solver load exactly
 * what it needs for a planning run without dragging half the object graph into memory.
 */
@Entity
@Table(name = "employees")
public class Employee extends TenantScopedEntity {

    /** Home location. Additional cleared locations live in {@link #additionalLocationIds}. */
    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    /** Optional link to a login account; null for staff who do not use self-service. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Column(name = "email", length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 30)
    private EmploymentType employmentType = EmploymentType.PART_TIME;

    /** Gross hourly wage in the organization's currency; drives the cost constraints. */
    @Column(name = "hourly_wage", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyWage = BigDecimal.ZERO;

    @Column(name = "contract_hours_per_week", nullable = false, precision = 5, scale = 2)
    private BigDecimal contractHoursPerWeek = BigDecimal.ZERO;

    @Column(name = "minimum_hours_per_week", nullable = false, precision = 5, scale = 2)
    private BigDecimal minimumHoursPerWeek = BigDecimal.ZERO;

    @Column(name = "maximum_hours_per_week", nullable = false, precision = 5, scale = 2)
    private BigDecimal maximumHoursPerWeek = new BigDecimal("48.00");

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "employee_skills", joinColumns = @JoinColumn(name = "employee_id"))
    @Column(name = "skill_id", nullable = false)
    private Set<UUID> skillIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "employee_departments", joinColumns = @JoinColumn(name = "employee_id"))
    @Column(name = "department_id", nullable = false)
    private Set<UUID> departmentIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "employee_locations", joinColumns = @JoinColumn(name = "employee_id"))
    @Column(name = "location_id", nullable = false)
    private Set<UUID> additionalLocationIds = new HashSet<>();

    protected Employee() {
        // for JPA
    }

    public Employee(UUID organizationId, UUID locationId, String firstName, String lastName) {
        setOrganizationId(organizationId);
        this.locationId = locationId;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    /** True if this employee may be scheduled at {@code candidateLocationId} at all. */
    public boolean isClearedForLocation(UUID candidateLocationId) {
        return locationId.equals(candidateLocationId) || additionalLocationIds.contains(candidateLocationId);
    }

    /** True if the employee holds every skill in {@code requiredSkillIds} (empty set = trivially true). */
    public boolean hasAllSkills(Set<UUID> requiredSkillIds) {
        return skillIds.containsAll(requiredSkillIds);
    }

    public boolean worksInDepartment(UUID departmentId) {
        return departmentIds.contains(departmentId);
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(EmploymentType employmentType) {
        this.employmentType = employmentType;
    }

    public BigDecimal getHourlyWage() {
        return hourlyWage;
    }

    public void setHourlyWage(BigDecimal hourlyWage) {
        this.hourlyWage = hourlyWage;
    }

    public BigDecimal getContractHoursPerWeek() {
        return contractHoursPerWeek;
    }

    public void setContractHoursPerWeek(BigDecimal contractHoursPerWeek) {
        this.contractHoursPerWeek = contractHoursPerWeek;
    }

    public BigDecimal getMinimumHoursPerWeek() {
        return minimumHoursPerWeek;
    }

    public void setMinimumHoursPerWeek(BigDecimal minimumHoursPerWeek) {
        this.minimumHoursPerWeek = minimumHoursPerWeek;
    }

    public BigDecimal getMaximumHoursPerWeek() {
        return maximumHoursPerWeek;
    }

    public void setMaximumHoursPerWeek(BigDecimal maximumHoursPerWeek) {
        this.maximumHoursPerWeek = maximumHoursPerWeek;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<UUID> getSkillIds() {
        return skillIds;
    }

    public void setSkillIds(Set<UUID> skillIds) {
        this.skillIds = new HashSet<>(skillIds);
    }

    public Set<UUID> getDepartmentIds() {
        return departmentIds;
    }

    public void setDepartmentIds(Set<UUID> departmentIds) {
        this.departmentIds = new HashSet<>(departmentIds);
    }

    public Set<UUID> getAdditionalLocationIds() {
        return additionalLocationIds;
    }

    public void setAdditionalLocationIds(Set<UUID> additionalLocationIds) {
        this.additionalLocationIds = new HashSet<>(additionalLocationIds);
    }
}
