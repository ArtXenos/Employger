package com.employger;

import com.employger.entity.User;
import com.employger.repository.UserRepository;
import com.employger.service.LoginAttemptService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Write requests must carry the token from the XSRF-TOKEN cookie in the
 * X-XSRF-TOKEN header, as csrf.js does. Uses a MockMvc built here, without
 * the test-wide default token from CsrfTestConfiguration.
 */
@SpringBootTest
@Transactional
class CsrfProtectionIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private LoginAttemptService loginAttemptService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        loginAttemptService.reset();

        User user = new User();
        user.setUsername("csrf.admin");
        user.setPasswordHash(passwordEncoder.encode("correct-horse"));
        user.setRole(User.Role.ADMINISTRATOR);
        user.setStatus(User.Status.ACTIVE);
        userRepository.save(user);
    }

    @Test
    void loginWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials()))
                .andExpect(status().isForbidden())
                .andExpect(content().string(
                        "Your security token is missing or expired. Please reload the page and try again."));
    }

    @Test
    void loginWithCookieTokenInHeaderSucceeds() throws Exception {
        Cookie token = fetchTokenCookie();

        mockMvc.perform(post("/api/auth/login")
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials()))
                .andExpect(status().isOk());
    }

    @Test
    void headerThatDoesNotMatchTheCookieIsRejected() throws Exception {
        Cookie token = fetchTokenCookie();

        mockMvc.perform(post("/api/auth/login")
                        .cookie(token)
                        .header("X-XSRF-TOKEN", "forged-" + token.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "csrf.admin", roles = "ADMINISTRATOR")
    void loggedInWriteWithoutTokenIsRejectedButReadIsAllowed() throws Exception {
        mockMvc.perform(post("/api/pay-cycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/pay-cycles"))
                .andExpect(status().isOk());
    }

    @Test
    void tokenEndpointSetsReadableCookie() throws Exception {
        Cookie token = fetchTokenCookie();

        assertThat(token.getValue()).isNotBlank();
        // csrf.js must be able to read it.
        assertThat(token.isHttpOnly()).isFalse();
    }

    private Cookie fetchTokenCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).as("XSRF-TOKEN cookie").isNotNull();
        return cookie;
    }

    private static String credentials() {
        return "{\"username\": \"csrf.admin\", \"password\": \"correct-horse\"}";
    }
}
