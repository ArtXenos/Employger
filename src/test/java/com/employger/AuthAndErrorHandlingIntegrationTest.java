package com.employger;

import com.employger.entity.Employee;
import com.employger.entity.User;
import com.employger.repository.AuditLogRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.UserRepository;
import com.employger.service.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login responses and API error mapping, against the in-memory H2 database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthAndErrorHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptService loginAttemptService;
    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        // Failure counts live in a singleton shared by every test in this context.
        loginAttemptService.reset();
        user("active.admin", User.Status.ACTIVE);
        user("inactive.admin", User.Status.INACTIVE);
    }

    // ---------- login ----------

    @Test
    void wrongPasswordReturns401WithMessage() throws Exception {
        login("active.admin", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid username or password."));
    }

    @Test
    void unknownUserAndInactiveUserGetTheSameResponseAsWrongPassword() throws Exception {
        login("no.such.user", "whatever")
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid username or password."));

        login("inactive.admin", "correct-horse")
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid username or password."));
    }

    @Test
    void successfulLoginRotatesSessionId() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String idBeforeLogin = session.getId();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("active.admin", "correct-horse")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNotNull();
        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(idBeforeLogin);
    }

    @Test
    void malformedLoginBodyReturns400NotForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());
    }

    // ---------- password-guessing limit ----------

    @Test
    void fiveFailuresLockTheAccountEvenForTheCorrectPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("active.admin", "guess-" + i).andExpect(status().isUnauthorized());
        }

        login("active.admin", "correct-horse")
                .andExpect(status().isTooManyRequests())
                // 900 s at the moment of locking, a little less by now.
                .andExpect(header().string("Retry-After", matchesPattern("8\\d\\d|900")))
                .andExpect(content().string(
                        "Too many failed login attempts. Please try again in 15 minutes."));
    }

    @Test
    void unknownUsernameIsLockedTheSameWaySoNothingIsRevealed() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("no.such.user", "guess-" + i).andExpect(status().isUnauthorized());
        }

        login("no.such.user", "guess-5")
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(
                        "Too many failed login attempts. Please try again in 15 minutes."));
    }

    @Test
    void lockOfOneUsernameDoesNotBlockAnother() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("no.such.user", "guess-" + i);
        }

        login("active.admin", "correct-horse").andExpect(status().isOk());
    }

    @Test
    void successfulLoginResetsTheUsernameCount() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("active.admin", "guess-" + i).andExpect(status().isUnauthorized());
        }
        login("active.admin", "correct-horse").andExpect(status().isOk());

        for (int i = 0; i < 4; i++) {
            login("active.admin", "guess-" + i).andExpect(status().isUnauthorized());
        }
        login("active.admin", "correct-horse").andExpect(status().isOk());
    }

    @Test
    void lockIsRecordedInTheAuditTrail() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("active.admin", "guess-" + i);
        }

        assertThat(auditLogRepository.findTop200ByOrderByAuditIdDesc())
                .anySatisfy(entry -> {
                    assertThat(entry.getAction()).isEqualTo("LOGIN_LOCKED");
                    assertThat(entry.getDescription()).contains("'active.admin'");
                    assertThat(entry.getUser()).isNull();
                });
    }

    // ---------- API error mapping ----------

    @Test
    @WithMockUser(username = "active.admin", roles = "ADMINISTRATOR")
    void missingRecordReturns404() throws Exception {
        mockMvc.perform(post("/api/attendance/employee/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendanceDate\": \"2026-10-05\", \"status\": \"PRESENT\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Employee not found"));
    }

    @Test
    @WithMockUser(username = "active.admin", roles = "ADMINISTRATOR")
    void validationErrorReturns400WithReason() throws Exception {
        mockMvc.perform(post("/api/pay-cycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cycleName": "Bad week", "startDate": "2026-10-01",
                                 "endDate": "2026-10-03", "payDate": "2026-10-05",
                                 "cycleType": "WEEKLY"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("exactly 7 days")));
    }

    @Test
    @WithMockUser(username = "active.admin", roles = "ADMINISTRATOR")
    void duplicateAttendanceDateReturns409() throws Exception {
        Employee employee = employee();
        String body = "{\"attendanceDate\": \"2026-10-05\", \"status\": \"PRESENT\", \"regularHours\": 8}";

        mockMvc.perform(post("/api/attendance/employee/{id}", employee.getEmployeeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/attendance/employee/{id}", employee.getEmployeeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("conflicts with existing data")));
    }

    @Test
    @WithMockUser(username = "active.admin", roles = "ADMINISTRATOR")
    void unknownPayrollReturns404NotServerError() throws Exception {
        mockMvc.perform(get("/api/payroll/preview/employee/999999/pay-cycle/999999"))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.ResultActions login(
            String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(username, password)));
    }

    private static String credentials(String username, String password) {
        return "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}";
    }

    private void user(String username, User.Status status) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("correct-horse"));
        user.setRole(User.Role.ADMINISTRATOR);
        user.setStatus(status);
        userRepository.save(user);
    }

    private Employee employee() {
        Employee employee = new Employee();
        employee.setFirstName("Test");
        employee.setLastName("Employee");
        employee.setEmail("attendance@example.test");
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setDateHired(LocalDate.of(2026, 1, 1));
        employee.setBaseSalary(new BigDecimal("25000.00"));
        return employeeRepository.save(employee);
    }
}
