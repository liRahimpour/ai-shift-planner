package com.aishiftplanner.scheduler.shared.seed;

import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;
import com.aishiftplanner.scheduler.auth.domain.Role;
import com.aishiftplanner.scheduler.auth.domain.User;
import com.aishiftplanner.scheduler.auth.infrastructure.UserRepository;
import com.aishiftplanner.scheduler.availability.domain.Availability;
import com.aishiftplanner.scheduler.availability.domain.AvailabilityType;
import com.aishiftplanner.scheduler.availability.domain.EmployeeComment;
import com.aishiftplanner.scheduler.availability.infrastructure.AvailabilityRepository;
import com.aishiftplanner.scheduler.availability.infrastructure.EmployeeCommentRepository;
import com.aishiftplanner.scheduler.employee.domain.Employee;
import com.aishiftplanner.scheduler.employee.domain.EmploymentType;
import com.aishiftplanner.scheduler.employee.domain.Skill;
import com.aishiftplanner.scheduler.employee.infrastructure.EmployeeRepository;
import com.aishiftplanner.scheduler.employee.infrastructure.SkillRepository;
import com.aishiftplanner.scheduler.organization.domain.Department;
import com.aishiftplanner.scheduler.organization.domain.Location;
import com.aishiftplanner.scheduler.organization.domain.Organization;
import com.aishiftplanner.scheduler.organization.infrastructure.DepartmentRepository;
import com.aishiftplanner.scheduler.organization.infrastructure.LocationRepository;
import com.aishiftplanner.scheduler.organization.infrastructure.OrganizationRepository;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriod;
import com.aishiftplanner.scheduler.planning.infrastructure.PlanningPeriodRepository;
import com.aishiftplanner.scheduler.staffing.application.StaffingRequirementService;
import com.aishiftplanner.scheduler.staffing.domain.StaffingRequirement;
import com.aishiftplanner.scheduler.staffing.infrastructure.StaffingRequirementRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates a realistic demo dataset: one restaurant in Mainz, three departments, 34 staff.
 *
 * <p>Guarded by {@code app.seed-demo-data} and off unless explicitly switched on. Seeding is
 * genuinely useful — a solver is impossible to evaluate against three employees, because
 * every plan looks the same — but a seeder that runs by default is one configuration mistake
 * away from inventing staff in a production database.
 *
 * <p>The data is deliberately awkward rather than tidy: 31 of 34 people submit availability
 * (so the dashboard's "3 missing" is real), wages and contract hours vary, and several
 * comments contradict or complicate the structured availability. A demo where everything
 * fits perfectly proves nothing about the solver.
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String ORGANIZATION_SLUG = "restaurant-group-mainz";
    private static final String DEFAULT_PASSWORD = "demo1234";
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    // Fixed seed: the demo must look the same every time it is shown, or a walkthrough
    // written on Monday no longer matches the screen on Tuesday.
    private final Random random = new Random(20260828L);

    private final OrganizationRepository organizationRepository;
    private final LocationRepository locationRepository;
    private final DepartmentRepository departmentRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PlanningPeriodRepository planningPeriodRepository;
    private final StaffingRequirementRepository staffingRequirementRepository;
    private final AvailabilityRepository availabilityRepository;
    private final EmployeeCommentRepository commentRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffingRequirementService staffingRequirementService;
    private final PlatformTransactionManager transactionManager;

    public DemoDataSeeder(
            OrganizationRepository organizationRepository,
            LocationRepository locationRepository,
            DepartmentRepository departmentRepository,
            SkillRepository skillRepository,
            UserRepository userRepository,
            EmployeeRepository employeeRepository,
            PlanningPeriodRepository planningPeriodRepository,
            StaffingRequirementRepository staffingRequirementRepository,
            AvailabilityRepository availabilityRepository,
            EmployeeCommentRepository commentRepository,
            PasswordEncoder passwordEncoder,
            StaffingRequirementService staffingRequirementService,
            PlatformTransactionManager transactionManager) {
        this.organizationRepository = organizationRepository;
        this.locationRepository = locationRepository;
        this.departmentRepository = departmentRepository;
        this.skillRepository = skillRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.planningPeriodRepository = planningPeriodRepository;
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.availabilityRepository = availabilityRepository;
        this.commentRepository = commentRepository;
        this.passwordEncoder = passwordEncoder;
        this.staffingRequirementService = staffingRequirementService;
        this.transactionManager = transactionManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (organizationRepository.existsBySlug(ORGANIZATION_SLUG)) {
            log.info("Demo data already present; skipping seeding.");
            return;
        }
        log.info("Seeding demo data...");

        // TransactionTemplate rather than a self-invoked @Transactional method: calling an
        // annotated method on `this` bypasses the Spring proxy that makes @Transactional work
        // at all, which would silently leave this whole block non-transactional.
        SeedResult result = new TransactionTemplate(transactionManager).execute(status -> seedCoreData());

        // Deliberately outside the transaction above: generateShifts() writes an audit log
        // entry in its own REQUIRES_NEW transaction, which would not yet be able to see the
        // organization row if it were still uncommitted in an enclosing transaction.
        generateShiftsAsAdmin(result.admin(), result.period().getId());

        log.info(
                "Demo data seeded: organization={} location={} employees={} period={}..{}",
                result.organization().getName(),
                result.location().getName(),
                result.employees().size(),
                result.period().getStartDate(),
                result.period().getEndDate());
        log.info("Demo logins (password '{}'): manager@demo.local, employee@demo.local", DEFAULT_PASSWORD);
    }

    private record SeedResult(
            Organization organization, Location location, User admin, List<Employee> employees, PlanningPeriod period) {}

    private SeedResult seedCoreData() {
        Organization organization = organizationRepository.save(
                new Organization("Restaurant Group GmbH", ORGANIZATION_SLUG));
        UUID orgId = organization.getId();

        Location mainz = locationRepository.save(new Location(orgId, "Mainz Innenstadt", "Europe/Berlin"));
        mainz.setCity("Mainz");
        mainz.setCountryCode("DE");
        locationRepository.save(mainz);

        Department kitchen = departmentRepository.save(new Department(orgId, mainz.getId(), "Küche"));
        Department counter = departmentRepository.save(new Department(orgId, mainz.getId(), "Theke"));
        Department bar = departmentRepository.save(new Department(orgId, mainz.getId(), "Bar"));

        Map<String, Skill> skills = seedSkills(orgId);
        User admin = seedManagers(orgId);
        List<Employee> employees = seedEmployees(orgId, mainz.getId(), List.of(kitchen, counter, bar), skills);
        PlanningPeriod period = seedPlanningPeriod(orgId, mainz.getId());
        seedStaffingRequirements(orgId, mainz.getId(), period, kitchen, counter, bar, skills);
        seedAvailability(orgId, period, employees);
        seedComments(orgId, period, employees);

        return new SeedResult(organization, mainz, admin, employees, period);
    }

    private Map<String, Skill> seedSkills(UUID orgId) {
        Map<String, String> catalogue = new LinkedHashMap<>();
        catalogue.put("BAR", "Bar service");
        catalogue.put("KITCHEN", "Kitchen work");
        catalogue.put("COUNTER", "Counter service");
        catalogue.put("CASH_REGISTER", "Cash register");
        catalogue.put("SHIFT_LEAD", "Shift lead");
        catalogue.put("OPENING", "Opening shift");
        catalogue.put("CLOSING", "Closing shift");
        catalogue.put("COFFEE_MACHINE", "Coffee machine");
        catalogue.put("FOOD_PREPARATION", "Food preparation");

        Map<String, Skill> saved = new LinkedHashMap<>();
        catalogue.forEach((code, name) -> saved.put(code, skillRepository.save(new Skill(orgId, code, name))));
        return saved;
    }

    private User seedManagers(UUID orgId) {
        User admin = new User(
                orgId, "admin@demo.local", passwordEncoder.encode(DEFAULT_PASSWORD), "Olivia", "Admin");
        admin.setRoles(EnumSet.of(Role.ORG_ADMIN));
        userRepository.save(admin);

        User manager = new User(
                orgId, "manager@demo.local", passwordEncoder.encode(DEFAULT_PASSWORD), "Mia", "Manager");
        manager.setRoles(EnumSet.of(Role.SHIFT_MANAGER, Role.LOCATION_MANAGER));
        userRepository.save(manager);
        return admin;
    }

    private List<Employee> seedEmployees(
            UUID orgId, UUID locationId, List<Department> departments, Map<String, Skill> skills) {

        String[][] people = {
            {"Anna", "Becker"}, {"Ben", "Schmidt"}, {"Clara", "Fischer"}, {"David", "Weber"},
            {"Elena", "Meyer"}, {"Felix", "Wagner"}, {"Greta", "Schulz"}, {"Hannes", "Hoffmann"},
            {"Ines", "Schäfer"}, {"Jonas", "Koch"}, {"Katja", "Bauer"}, {"Lukas", "Richter"},
            {"Maria", "Klein"}, {"Nico", "Wolf"}, {"Olga", "Neumann"}, {"Paul", "Schwarz"},
            {"Quirin", "Zimmermann"}, {"Rosa", "Braun"}, {"Sarah", "Krüger"}, {"Timo", "Hofmann"},
            {"Ulla", "Hartmann"}, {"Viktor", "Lange"}, {"Wanda", "Werner"}, {"Xenia", "Schmitt"},
            {"Yannick", "Krause"}, {"Zoe", "Meier"}, {"Adrian", "Lehmann"}, {"Bianca", "Schmid"},
            {"Cem", "Schulze"}, {"Doris", "Maier"}, {"Emil", "Köhler"}, {"Frieda", "Herrmann"},
            {"Gustav", "Walter"}, {"Helena", "König"},
        };

        EmploymentType[] types = {
            EmploymentType.FULL_TIME, EmploymentType.PART_TIME, EmploymentType.MINIJOB,
            EmploymentType.WORKING_STUDENT, EmploymentType.PART_TIME, EmploymentType.FULL_TIME,
        };

        List<Employee> employees = new ArrayList<>(people.length);
        for (int i = 0; i < people.length; i++) {
            Department department = departments.get(i % departments.size());
            EmploymentType type = types[i % types.length];

            Employee employee = new Employee(orgId, locationId, people[i][0], people[i][1]);
            employee.setEmail(
                    (people[i][0] + "." + people[i][1]).toLowerCase().replace("ä", "ae")
                            .replace("ö", "oe").replace("ü", "ue") + "@demo.local");
            employee.setEmploymentType(type);
            employee.setDepartmentIds(Set.of(department.getId()));
            employee.setSkillIds(skillsFor(department.getName(), skills, i));

            // Wages and hours vary by contract type and a little at random, so the
            // cost-optimized plan has something real to optimize.
            BigDecimal wage = switch (type) {
                case FULL_TIME -> new BigDecimal("16.50").add(BigDecimal.valueOf(random.nextInt(300) / 100.0));
                case MINIJOB -> new BigDecimal("13.00").add(BigDecimal.valueOf(random.nextInt(150) / 100.0));
                case WORKING_STUDENT -> new BigDecimal("14.00").add(BigDecimal.valueOf(random.nextInt(200) / 100.0));
                default -> new BigDecimal("14.50").add(BigDecimal.valueOf(random.nextInt(250) / 100.0));
            };
            employee.setHourlyWage(wage.setScale(2, java.math.RoundingMode.HALF_UP));

            double contract = switch (type) {
                case FULL_TIME -> 38;
                case PART_TIME -> 20 + random.nextInt(10);
                case MINIJOB -> 8 + random.nextInt(4);
                case WORKING_STUDENT -> 16 + random.nextInt(4);
                default -> 20;
            };
            employee.setContractHoursPerWeek(BigDecimal.valueOf(contract));
            employee.setMinimumHoursPerWeek(BigDecimal.valueOf(Math.max(0, contract - 8)));
            employee.setMaximumHoursPerWeek(BigDecimal.valueOf(Math.min(48, contract + 10)));

            employees.add(employeeRepository.save(employee));
        }

        // One employee gets a login, so the demo can show the self-service side.
        User employeeUser = new User(
                orgId, "employee@demo.local", passwordEncoder.encode(DEFAULT_PASSWORD), "Anna", "Becker");
        employeeUser.setRoles(EnumSet.of(Role.EMPLOYEE));
        User savedUser = userRepository.save(employeeUser);
        Employee anna = employees.get(0);
        anna.setUserId(savedUser.getId());
        employeeRepository.save(anna);

        return employees;
    }

    private Set<UUID> skillsFor(String departmentName, Map<String, Skill> skills, int index) {
        List<UUID> result = new ArrayList<>();
        switch (departmentName) {
            case "Küche" -> {
                result.add(skills.get("KITCHEN").getId());
                result.add(skills.get("FOOD_PREPARATION").getId());
            }
            case "Bar" -> {
                result.add(skills.get("BAR").getId());
                result.add(skills.get("CASH_REGISTER").getId());
            }
            default -> {
                result.add(skills.get("COUNTER").getId());
                result.add(skills.get("COFFEE_MACHINE").getId());
                result.add(skills.get("CASH_REGISTER").getId());
            }
        }
        // Closing and opening qualifications are scarce on purpose: they are what makes the
        // fairness constraints matter, since only some people can take the unpopular shifts.
        if (index % 3 == 0) {
            result.add(skills.get("CLOSING").getId());
        }
        if (index % 4 == 0) {
            result.add(skills.get("OPENING").getId());
        }
        if (index % 7 == 0) {
            result.add(skills.get("SHIFT_LEAD").getId());
        }
        return Set.copyOf(result);
    }

    private PlanningPeriod seedPlanningPeriod(UUID orgId, UUID locationId) {
        // Next Monday, so the demo period is always in the future no matter when it is run.
        LocalDate today = LocalDate.now(ZONE);
        long daysUntilMonday = (8 - today.getDayOfWeek().getValue()) % 7;
        LocalDate start = today.plusDays(daysUntilMonday == 0 ? 7 : daysUntilMonday);

        // The availability deadline sits 5 days before the period starts (the Wednesday
        // before a Monday start). "Next Monday" alone is not enough of a buffer for that to
        // land in the future: seeded on a Wednesday through Sunday, the nearest Monday is
        // fewer than 5 days out and the deadline would already be in the past the moment the
        // demo data exists. Roll forward an extra week whenever that would happen.
        if (!start.minusDays(5).isAfter(today)) {
            start = start.plusWeeks(1);
        }
        LocalDate end = start.plusDays(6);

        PlanningPeriod period = new PlanningPeriod(
                orgId,
                locationId,
                start,
                end,
                start.minusDays(5).atTime(LocalTime.of(18, 0)).atZone(ZONE).toInstant(),
                null);
        return planningPeriodRepository.save(period);
    }

    /**
     * Materializes concrete shifts from the staffing requirements just seeded, exactly as a
     * manager does by hand on the staffing screen. Without this, the demo period has staffing
     * requirements but no shifts, and plan generation fails immediately with "No shifts exist
     * for this planning period" - a dead end for anyone trying the demo.
     *
     * <p>{@link StaffingRequirementService#generateShifts} is tenant- and audit-aware, so it
     * needs a real {@code Authentication} in context; there is none this early at startup, so
     * one is set up for the seeded admin just for this call and cleared immediately after.
     */
    private void generateShiftsAsAdmin(User admin, UUID periodId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.of(admin), null, AuthenticatedUser.of(admin).getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            staffingRequirementService.generateShifts(periodId);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void seedStaffingRequirements(
            UUID orgId,
            UUID locationId,
            PlanningPeriod period,
            Department kitchen,
            Department counter,
            Department bar,
            Map<String, Skill> skills) {

        for (LocalDate date = period.getStartDate();
                !date.isAfter(period.getEndDate());
                date = date.plusDays(1)) {

            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY
                    || date.getDayOfWeek() == DayOfWeek.SUNDAY;

            add(orgId, locationId, period, kitchen, date, "10:00", "16:00", false,
                    weekend ? 3 : 2, weekend ? 3 : 2, weekend ? 4 : 3, Map.of());
            add(orgId, locationId, period, kitchen, date, "16:00", "23:00", false,
                    weekend ? 3 : 2, weekend ? 4 : 3, weekend ? 5 : 4,
                    Map.of(skills.get("CLOSING").getId(), 1));
            add(orgId, locationId, period, counter, date, "08:00", "14:00", false,
                    2, weekend ? 3 : 2, 4,
                    Map.of(skills.get("OPENING").getId(), 1));
            add(orgId, locationId, period, counter, date, "14:00", "20:00", false,
                    2, 2, 3, Map.of());

            if (weekend || date.getDayOfWeek() == DayOfWeek.FRIDAY) {
                // The signature case: a bar block that runs past midnight.
                add(orgId, locationId, period, bar, date, "18:00", "02:00", true,
                        3, 4, 5,
                        Map.of(skills.get("BAR").getId(), 1, skills.get("CLOSING").getId(), 1));
            } else {
                add(orgId, locationId, period, bar, date, "18:00", "23:00", false,
                        2, 2, 3, Map.of(skills.get("BAR").getId(), 1));
            }
        }
    }

    private void add(
            UUID orgId,
            UUID locationId,
            PlanningPeriod period,
            Department department,
            LocalDate date,
            String from,
            String to,
            boolean crossesMidnight,
            int minimum,
            int preferred,
            int maximum,
            Map<UUID, Integer> requiredSkills) {

        StaffingRequirement requirement = new StaffingRequirement(
                orgId,
                locationId,
                department.getId(),
                period.getId(),
                date,
                LocalTime.parse(from),
                LocalTime.parse(to),
                crossesMidnight);
        requirement.setMinimumStaff(minimum);
        requirement.setPreferredStaff(preferred);
        requirement.setMaximumStaff(maximum);
        requirement.setRequiredSkills(requiredSkills);
        staffingRequirementRepository.save(requirement);
    }

    private void seedAvailability(UUID orgId, PlanningPeriod period, List<Employee> employees) {
        // 31 of 34 submit. The three who do not are the point: the manager dashboard's
        // "3 missing" has to be a real number, not a mock-up.
        int submitting = employees.size() - 3;

        for (int i = 0; i < submitting; i++) {
            Employee employee = employees.get(i);
            for (LocalDate date = period.getStartDate();
                    !date.isAfter(period.getEndDate());
                    date = date.plusDays(1)) {

                int roll = random.nextInt(100);
                if (roll < 15) {
                    availabilityRepository.save(new Availability(
                            orgId, period.getId(), employee.getId(), date,
                            AvailabilityType.UNAVAILABLE, null, null));
                } else if (roll < 35) {
                    availabilityRepository.save(new Availability(
                            orgId, period.getId(), employee.getId(), date,
                            AvailabilityType.PREFERRED,
                            LocalTime.of(10, 0), LocalTime.of(18, 0)));
                } else if (roll < 50) {
                    // A split day: two windows with a gap, which the solver has to respect.
                    availabilityRepository.save(new Availability(
                            orgId, period.getId(), employee.getId(), date,
                            AvailabilityType.AVAILABLE, LocalTime.of(10, 0), LocalTime.of(14, 0)));
                    availabilityRepository.save(new Availability(
                            orgId, period.getId(), employee.getId(), date,
                            AvailabilityType.AVAILABLE, LocalTime.of(18, 0), LocalTime.of(23, 59)));
                } else {
                    availabilityRepository.save(new Availability(
                            orgId, period.getId(), employee.getId(), date,
                            AvailabilityType.AVAILABLE, null, null));
                }
            }
        }
    }

    private void seedComments(UUID orgId, PlanningPeriod period, List<Employee> employees) {
        List<String> comments = List.of(
                "Samstag kann ich arbeiten, aber bitte erst ab 17 Uhr, weil ich vorher Uni habe.",
                "Ich würde diese Woche gern ein paar Stunden mehr machen, wenn möglich.",
                "Bitte keine zwei Schließdienste hintereinander, das letzte Mal war hart.",
                "Am Sonntag habe ich einen Zug um 19:00, danach geht leider nichts mehr.",
                "Freitag Bar wäre super, das mache ich am liebsten.",
                "Ich bin nächste Woche krank gemeldet gewesen, bin aber wieder fit.");

        for (int i = 0; i < comments.size(); i++) {
            commentRepository.save(new EmployeeComment(
                    orgId, period.getId(), employees.get(i).getId(), comments.get(i)));
        }
    }
}
