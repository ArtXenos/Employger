package com.employger;

import com.employger.config.PasswordChangeRequiredFilter;
import com.employger.entity.AuditLog;
import com.employger.entity.PasswordResetRequest;
import com.employger.entity.User;
import com.employger.repository.AuditLogRepository;
import com.employger.repository.PasswordResetRequestRepository;
import com.employger.repository.UserRepository;
import com.employger.service.LoginAttemptService;
import com.employger.service.password.TemporaryPassword;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Forgot password: the request from the login page, the administrator's
 * temporary password, and the forced change at the next login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetIntegrationTest {

    private static final String SENT = "Your request has been sent. If this username has an active account, "
            + "an administrator will contact you and give you a temporary password in person.";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordResetRequestRepository requestRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        loginAttemptService.reset();
        user("reset.admin", User.Role.ADMINISTRATOR, User.Status.ACTIVE);
        user("forgetful.hr", User.Role.HR_MANAGER, User.Status.ACTIVE);
        user("former.hr", User.Role.HR_MANAGER, User.Status.INACTIVE);
    }

    // ---------- the request ----------

    @Test
    void everyUsernameGetsTheSameAnswerButOnlyActiveLoginsAreQueuedOnce() throws Exception {
        forgot("forgetful.hr").andExpect(status().isOk()).andExpect(content().string(SENT));
        forgot("forgetful.hr").andExpect(status().isOk()).andExpect(content().string(SENT));
        forgot("no.such.user").andExpect(status().isOk()).andExpect(content().string(SENT));
        forgot("former.hr").andExpect(status().isOk()).andExpect(content().string(SENT));

        List<PasswordResetRequest> pending = requestRepository
                .findByStatusOrderByRequestedAtAscRequestIdAsc(PasswordResetRequest.Status.PENDING);
        assertThat(pending).extracting(request -> request.getUser().getUsername())
                .containsExactly("forgetful.hr");
        assertThat(pending.get(0).getRequestedFrom()).isEqualTo("127.0.0.1");
    }

    @Test
    void blankUsernameIsRefused() throws Exception {
        forgot("  ").andExpect(status().isBadRequest()).andExpect(content().string("Enter your username."));
    }

    @Test
    void administratorsSeeTheRequestsAndOthersDoNot() throws Exception {
        forgot("forgetful.hr");

        MockHttpSession admin = login("reset.admin", "correct-horse-battery");
        mockMvc.perform(get("/api/users/password-resets/count").session(admin))
                .andExpect(jsonPath("$.pending").value(1));
        mockMvc.perform(get("/api/users/password-resets").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("forgetful.hr"))
                .andExpect(jsonPath("$[0].role").value("HR_MANAGER"));

        MockHttpSession hr = login("forgetful.hr", "correct-horse-battery");
        mockMvc.perform(get("/api/users/password-resets").session(hr)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/password-resets/count").session(hr)).andExpect(status().isForbidden());
    }

    // ---------- temporary password and the forced change ----------

    @Test
    void temporaryPasswordWorksOnlyToChooseANewOne() throws Exception {
        forgot("forgetful.hr");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(credentials("forgetful.hr", "guess-" + i)));
        }

        MockHttpSession admin = login("reset.admin", "correct-horse-battery");
        String temporary = temporaryPassword(admin, "forgetful.hr");
        assertThat(temporary).matches("[a-zA-Z2-9]{5}-[a-zA-Z2-9]{5}-[a-zA-Z2-9]{5}");

        // The request is answered, the lockout from the guesses is lifted,
        // and the old password no longer works.
        assertThat(requestRepository.countByStatus(PasswordResetRequest.Status.PENDING)).isZero();
        loginRequest("forgetful.hr", "correct-horse-battery").andExpect(status().isUnauthorized());
        MockHttpSession hr = login("forgetful.hr", temporary);

        mockMvc.perform(get("/api/auth/me").session(hr))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        mockMvc.perform(get("/api/employees").session(hr))
                .andExpect(status().isForbidden())
                .andExpect(content().string(PasswordChangeRequiredFilter.MESSAGE));

        changePassword(hr, temporary, temporary)
                .andExpect(status().isBadRequest());
        changePassword(hr, temporary, "my-own-new-passphrase")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").session(hr))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
        mockMvc.perform(get("/api/employees").session(hr)).andExpect(status().isOk());
        loginRequest("forgetful.hr", temporary).andExpect(status().isUnauthorized());
        login("forgetful.hr", "my-own-new-passphrase");
    }

    @Test
    void theTemporaryPasswordIsNeitherStoredNorAuditedNorPrinted() throws Exception {
        forgot("forgetful.hr");
        MockHttpSession admin = login("reset.admin", "correct-horse-battery");

        String temporary = temporaryPassword(admin, "forgetful.hr");

        User user = userRepository.findByUsername("forgetful.hr").orElseThrow();
        assertThat(user.getPasswordHash()).doesNotContain(temporary);
        assertThat(user.isMustChangePassword()).isTrue();

        List<AuditLog> entries = auditLogRepository.findTop200ByOrderByAuditIdDesc();
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.getAction()).isEqualTo("PASSWORD_RESET_REQUESTED");
            assertThat(entry.getUser()).isNull();
        });
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.getAction()).isEqualTo("PASSWORD_RESET");
            assertThat(entry.getDescription()).contains("'forgetful.hr'").doesNotContain(temporary);
            assertThat(entry.getUser().getUsername()).isEqualTo("reset.admin");
        });

        assertThat(new TemporaryPassword(temporary).toString()).doesNotContain(temporary);
    }

    @Test
    void administratorCanResetWithoutARequestButNotTheirOwnOrAnInactiveLogin() throws Exception {
        MockHttpSession admin = login("reset.admin", "correct-horse-battery");

        temporaryPassword(admin, "forgetful.hr");

        mockMvc.perform(post("/api/users/{id}/temporary-password", idOf("reset.admin")).session(admin))
                .andExpect(status().isConflict())
                .andExpect(content().string("Use Change password for your own account."));
        mockMvc.perform(post("/api/users/{id}/temporary-password", idOf("former.hr")).session(admin))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/users").session(admin))
                .andExpect(jsonPath("$[?(@.username == 'forgetful.hr')].mustChangePassword").value(true));
    }

    @Test
    void dismissedRequestIsClosedOnceAndANewOneCanFollow() throws Exception {
        forgot("forgetful.hr");
        Integer requestId = requestRepository
                .findByStatusOrderByRequestedAtAscRequestIdAsc(PasswordResetRequest.Status.PENDING)
                .get(0).getRequestId();
        MockHttpSession admin = login("reset.admin", "correct-horse-battery");

        mockMvc.perform(post("/api/users/password-resets/{id}/dismiss", requestId).session(admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/users/password-resets/{id}/dismiss", requestId).session(admin))
                .andExpect(status().isConflict())
                .andExpect(content().string("This request has already been handled."));

        // The password is unchanged, and the person can ask again.
        login("forgetful.hr", "correct-horse-battery");
        forgot("forgetful.hr");
        assertThat(requestRepository.countByStatus(PasswordResetRequest.Status.PENDING)).isEqualTo(1);
    }

    // ---------- helpers ----------

    private ResultActions forgot(String username) throws Exception {
        return mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"" + username + "\"}"));
    }

    private String temporaryPassword(MockHttpSession admin, String username) throws Exception {
        String body = mockMvc.perform(post("/api/users/{id}/temporary-password", idOf(username)).session(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(body, "$.username")).isEqualTo(username);
        return JsonPath.read(body, "$.temporaryPassword");
    }

    private ResultActions changePassword(MockHttpSession session, String current, String next) throws Exception {
        return mockMvc.perform(put("/api/me/password")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"" + current + "\", \"newPassword\": \"" + next + "\"}"));
    }

    private ResultActions loginRequest(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(username, password)));
    }

    private MockHttpSession login(String username, String password) throws Exception {
        return (MockHttpSession) loginRequest(username, password)
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private static String credentials(String username, String password) {
        return "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}";
    }

    private Integer idOf(String username) {
        return userRepository.findByUsername(username).orElseThrow().getUserId();
    }

    private void user(String username, User.Role role, User.Status status) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("correct-horse-battery"));
        user.setRole(role);
        user.setStatus(status);
        userRepository.save(user);
    }
}
