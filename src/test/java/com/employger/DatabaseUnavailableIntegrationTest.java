package com.employger;

import com.employger.repository.EmployeeRepository;
import com.employger.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Simulates MariaDB being unreachable: the repositories throw the same
 * exception Spring raises when no connection can be obtained. The user must
 * be told the database is down, not that their password is wrong.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DatabaseUnavailableIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @Test
    void loginReportsDatabaseOutageAs503() throws Exception {
        when(userRepository.findByUsername(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Connection refused"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"admin\", \"password\": \"x\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("database")))
                .andExpect(content().string(not(containsString("Invalid username"))))
                .andExpect(content().string(not(containsString("Connection refused"))));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void apiReportsDatabaseOutageAs503WithoutInternalDetails() throws Exception {
        when(employeeRepository.findAll())
                .thenThrow(new DataAccessResourceFailureException("Connection refused"));

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("database is unavailable")))
                .andExpect(content().string(not(containsString("Connection refused"))));
    }
}
