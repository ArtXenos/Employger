package com.employger;

import com.employger.entity.AuditLog;
import com.employger.entity.Employee;
import com.employger.entity.User;
import com.employger.repository.AuditLogRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Input validation and response shape for /api/employees and /api/users.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "acting.admin", roles = "ADMINISTRATOR")
class EmployeeAndUserValidationIntegrationTest {

    private static final String VALID_EMPLOYEE = """
            {"firstName": " Maria ", "lastName": "Santos", "dateOfBirth": "1995-03-15",
             "email": "maria@example.test", "phone": "  ", "department": "Finance",
             "baseSalary": 30000, "dateHired": "2026-09-01", "employmentStatus": "ACTIVE"}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User actingAdmin;

    @BeforeEach
    void setUp() {
        actingAdmin = user("acting.admin", User.Role.ADMINISTRATOR, null);
    }

    // ---------- employees ----------

    @Test
    void createTrimsTextStoresBlankAsNullAndAudits() throws Exception {
        sendEmployee(post("/api/employees"), VALID_EMPLOYEE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Maria"))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.profileImage").doesNotExist());

        assertThat(latestAudit().getAction()).isEqualTo("EMPLOYEE_CREATED");
    }

    @Test
    void emptyEmployeeListsEveryMissingField() throws Exception {
        sendEmployee(post("/api/employees"), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(
                        "Base salary is required. Date hired is required. Date of birth is required. "
                                + "Email is required. First name is required. Last name is required."));
    }

    @Test
    void rejectsBadEmailNegativeSalaryAndOverlongName() throws Exception {
        String body = VALID_EMPLOYEE
                .replace("maria@example.test", "not-an-email")
                .replace("30000", "-1")
                .replace("Santos", "x".repeat(101));

        sendEmployee(post("/api/employees"), body)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Email is not a valid email address.")))
                .andExpect(content().string(containsString("Base salary cannot be negative.")))
                .andExpect(content().string(containsString("Last name must be at most 100 characters.")));
    }

    @Test
    void rejectsHireDateBeforeBirth() throws Exception {
        sendEmployee(post("/api/employees"), VALID_EMPLOYEE.replace("2026-09-01", "1990-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Date hired must be after the date of birth."));
    }

    @Test
    void duplicateEmailIsRejectedIgnoringCaseButOwnEmailIsFine() throws Exception {
        Employee existing = employee("taken@example.test");
        Employee other = employee("other@example.test");

        sendEmployee(post("/api/employees"), VALID_EMPLOYEE.replace("maria@example.test", "TAKEN@example.test"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Another employee already uses this email address."));

        sendEmployee(put("/api/employees/{id}", other.getEmployeeId()),
                VALID_EMPLOYEE.replace("maria@example.test", "taken@example.test"))
                .andExpect(status().isConflict());

        sendEmployee(put("/api/employees/{id}", existing.getEmployeeId()),
                VALID_EMPLOYEE.replace("maria@example.test", "taken@example.test"))
                .andExpect(status().isOk());
    }

    @Test
    void updateKeepsProfileImageAndStatusWhenNotSent() throws Exception {
        Employee employee = employee("photo@example.test");
        employee.setProfileImage(new byte[] { 1, 2, 3 });
        employee.setEmploymentStatus(Employee.EmploymentStatus.INACTIVE);
        employeeRepository.saveAndFlush(employee);

        sendEmployee(put("/api/employees/{id}", employee.getEmployeeId()),
                VALID_EMPLOYEE.replace("maria@example.test", "photo@example.test")
                        .replace(", \"employmentStatus\": \"ACTIVE\"", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employmentStatus").value("INACTIVE"));

        Employee reloaded = employeeRepository.findById(employee.getEmployeeId()).orElseThrow();
        assertThat(reloaded.getProfileImage()).containsExactly(1, 2, 3);
    }

    @Test
    void unknownEmployeeReturns404WithMessage() throws Exception {
        mockMvc.perform(get("/api/employees/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Employee not found"));

        sendEmployee(put("/api/employees/{id}", 999999), VALID_EMPLOYEE)
                .andExpect(status().isNotFound());
    }

    // ---------- users ----------

    @Test
    void shortAndOverlongPasswordsAreRejected() throws Exception {
        sendUser(post("/api/users"), "new.user", "short-pass", "HR_MANAGER", "ACTIVE", null)
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Password must be at least 12 characters."));

        sendUser(post("/api/users"), "new.user", "x".repeat(73), "HR_MANAGER", "ACTIVE", null)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Password is too long")));
    }

    @Test
    void invalidUsernameAndMissingRoleAreListed() throws Exception {
        sendUser(post("/api/users"), "a b", "long-enough-password", null, "ACTIVE", null)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Role is required.")))
                .andExpect(content().string(containsString("Username must be 3 to 100 characters")));
    }

    @Test
    void createdUserIsAuditedWithoutPasswordAndResponseHasNoHash() throws Exception {
        sendUser(post("/api/users"), "new.user", "long-enough-password", "HR_MANAGER", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        AuditLog entry = latestAudit();
        assertThat(entry.getAction()).isEqualTo("USER_CREATED");
        assertThat(entry.getDescription()).contains("'new.user'", "HR_MANAGER")
                .doesNotContain("long-enough-password");
    }

    @Test
    void duplicateUsernameIgnoringCaseAndSecondLoginForEmployeeAreRejected() throws Exception {
        Employee employee = employee("linked@example.test");
        user("linked.user", User.Role.EMPLOYEE, employee);

        sendUser(post("/api/users"), "LINKED.user", "long-enough-password", "EMPLOYEE", null, null)
                .andExpect(status().isConflict())
                .andExpect(content().string("This username is already taken."));

        sendUser(post("/api/users"), "second.login", "long-enough-password", "EMPLOYEE", null,
                employee.getEmployeeId())
                .andExpect(status().isConflict())
                .andExpect(content().string("This employee already has a login."));
    }

    @Test
    void onlyActiveAdministratorCannotBeDemotedDeactivatedOrDeleted() throws Exception {
        sendUser(put("/api/users/{id}", actingAdmin.getUserId()),
                "acting.admin", "", "HR_MANAGER", "ACTIVE", null)
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("only active administrator")));

        sendUser(put("/api/users/{id}", actingAdmin.getUserId()),
                "acting.admin", "", "ADMINISTRATOR", "INACTIVE", null)
                .andExpect(status().isConflict());

        assertThat(userRepository.findById(actingAdmin.getUserId()).orElseThrow().getRole())
                .isEqualTo(User.Role.ADMINISTRATOR);
    }

    @Test
    @WithMockUser(username = "someone.else", roles = "ADMINISTRATOR")
    void onlyActiveAdministratorCannotBeDeletedByAnotherSession() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", actingAdmin.getUserId()))
                .andExpect(status().isConflict())
                .andExpect(content().string("The only active administrator cannot be deleted."));
    }

    @Test
    void administratorCanBeDemotedOnceAnotherExists() throws Exception {
        user("second.admin", User.Role.ADMINISTRATOR, null);

        sendUser(put("/api/users/{id}", actingAdmin.getUserId()),
                "acting.admin", "", "HR_MANAGER", "ACTIVE", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("HR_MANAGER"));

        assertThat(latestAudit().getDescription())
                .isEqualTo("User #" + actingAdmin.getUserId() + ": role ADMINISTRATOR -> HR_MANAGER");
    }

    @Test
    void cannotDeleteOwnAccount() throws Exception {
        user("second.admin", User.Role.ADMINISTRATOR, null);

        mockMvc.perform(delete("/api/users/{id}", actingAdmin.getUserId()))
                .andExpect(status().isConflict())
                .andExpect(content().string("You cannot delete your own account."));
    }

    @Test
    void passwordChangeIsValidatedAndAuditedWithoutTheValue() throws Exception {
        User target = user("target.user", User.Role.EMPLOYEE, null);
        String oldHash = target.getPasswordHash();

        sendUser(put("/api/users/{id}", target.getUserId()),
                "target.user", "too-short", "EMPLOYEE", "ACTIVE", null)
                .andExpect(status().isBadRequest());

        sendUser(put("/api/users/{id}", target.getUserId()),
                "target.user", "brand-new-password", "EMPLOYEE", "ACTIVE", null)
                .andExpect(status().isOk());

        assertThat(userRepository.findById(target.getUserId()).orElseThrow().getPasswordHash())
                .isNotEqualTo(oldHash);
        assertThat(latestAudit().getDescription())
                .isEqualTo("User #" + target.getUserId() + ": password changed")
                .doesNotContain("brand-new-password");
    }

    @Test
    void blankPasswordOnUpdateKeepsTheCurrentOne() throws Exception {
        User target = user("target.user", User.Role.EMPLOYEE, null);
        String oldHash = target.getPasswordHash();

        sendUser(put("/api/users/{id}", target.getUserId()),
                "target.user", "", "GENERAL_MANAGER", "ACTIVE", null)
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("password"))));

        assertThat(userRepository.findById(target.getUserId()).orElseThrow().getPasswordHash())
                .isEqualTo(oldHash);
    }

    // ---------- helpers ----------

    private ResultActions sendEmployee(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String body) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions sendUser(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String username, String password, String role, String status, Integer employeeId)
            throws Exception {

        String body = "{\"username\": \"" + username + "\", \"password\": \"" + password + "\""
                + (role != null ? ", \"role\": \"" + role + "\"" : "")
                + (status != null ? ", \"status\": \"" + status + "\"" : "")
                + (employeeId != null ? ", \"employeeId\": " + employeeId : "")
                + "}";

        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private AuditLog latestAudit() {
        return auditLogRepository.findTop200ByOrderByAuditIdDesc().getFirst();
    }

    private User user(String username, User.Role role, Employee employee) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("correct-horse-battery"));
        user.setRole(role);
        user.setStatus(User.Status.ACTIVE);
        user.setEmployee(employee);
        return userRepository.save(user);
    }

    private Employee employee(String email) {
        Employee employee = new Employee();
        employee.setFirstName("Test");
        employee.setLastName("Person");
        employee.setEmail(email);
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setDateHired(LocalDate.of(2026, 1, 1));
        employee.setBaseSalary(new BigDecimal("25000.00"));
        return employeeRepository.save(employee);
    }
}
