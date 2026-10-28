package com.employger;

import com.employger.entity.AuditLog;
import com.employger.entity.ChoiceOption;
import com.employger.entity.Department;
import com.employger.repository.AuditLogRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Department and Job Title choice lists (PM item F, migration V16):
 * HR and administrators add entries, job titles depend on the department,
 * and retired entries can't be newly chosen.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "hr.lists", roles = "HR_MANAGER")
class ChoiceListIntegrationTest {

    private static final String EMPLOYEE = """
            {"firstName": "Maria", "lastName": "Santos", "dateOfBirth": "1995-03-15",
             "email": "%s", "baseSalary": 30000, "dateHired": "2026-09-01",
             "employmentStatus": "ACTIVE", "departmentId": %s, "jobTitleId": %s}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuditLogRepository auditLogRepository;

    // ---------- adding entries ----------

    @Test
    void hrAddsDepartmentsAndTheirJobTitlesListedAToZ() throws Exception {
        int finance = addDepartment("  Finance  ");
        int hr = addDepartment("Human   Resources");
        addJobTitle(finance, "Payroll Clerk");
        addJobTitle(finance, "Accountant");
        addJobTitle(hr, "Payroll Clerk");

        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Finance"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].jobTitles[0].name").value("Accountant"))
                .andExpect(jsonPath("$[0].jobTitles[1].name").value("Payroll Clerk"))
                .andExpect(jsonPath("$[1].name").value("Human Resources"))
                .andExpect(jsonPath("$[1].jobTitles[0].name").value("Payroll Clerk"));

        assertThat(auditActions()).contains("DEPARTMENT_ADDED", "JOB_TITLE_ADDED");
        assertThat(auditLogRepository.findTop200ByOrderByAuditIdDesc())
                .extracting(AuditLog::getDescription)
                .contains("Added job title \"Payroll Clerk\" in Human Resources");
    }

    @Test
    void namesAreUniqueIgnoringCaseWithinTheirList() throws Exception {
        int finance = addDepartment("Finance");
        addJobTitle(finance, "Accountant");

        send(post("/api/departments"), "{\"name\": \"FINANCE\"}")
                .andExpect(status().isConflict())
                .andExpect(content().string("There is already a department called \"FINANCE\"."));
        send(post("/api/departments/{id}/job-titles", finance), "{\"name\": \"accountant\"}")
                .andExpect(status().isConflict());
        send(post("/api/departments"), "{\"name\": \"   \"}")
                .andExpect(status().isBadRequest());

        int sales = addDepartment("Sales");
        send(put("/api/departments/{id}", sales), "{\"name\": \"finance\"}")
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administratorsMayAddEntriesToo() throws Exception {
        addJobTitle(addDepartment("Operations"), "Driver");
    }

    @Test
    @WithMockUser(roles = "GENERAL_MANAGER")
    void generalManagersMayReadButNotChangeTheLists() throws Exception {
        mockMvc.perform(get("/api/departments")).andExpect(status().isOk());
        send(post("/api/departments"), "{\"name\": \"Finance\"}").andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeesCannotSeeTheLists() throws Exception {
        mockMvc.perform(get("/api/departments")).andExpect(status().isForbidden());
    }

    // ---------- on the employee ----------

    @Test
    void employeeGetsADepartmentAndOneOfItsJobTitles() throws Exception {
        int finance = addDepartment("Finance");
        int accountant = addJobTitle(finance, "Accountant");

        String body = createEmployee("maria@example.test", finance, accountant)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("Finance"))
                .andExpect(jsonPath("$.departmentId").value(finance))
                .andExpect(jsonPath("$.jobTitle").value("Accountant"))
                .andExpect(jsonPath("$.jobTitleId").value(accountant))
                .andReturn().getResponse().getContentAsString();

        // Renaming changes it for every employee who has it.
        send(put("/api/job-titles/{id}", accountant), "{\"name\": \"Senior Accountant\"}")
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/employees/{id}", JsonPath.<Integer>read(body, "$.employeeId")))
                .andExpect(jsonPath("$.jobTitle").value("Senior Accountant"));
        assertThat(auditActions()).contains("JOB_TITLE_RENAMED");
    }

    @Test
    void aJobTitleMustBelongToTheChosenDepartment() throws Exception {
        int finance = addDepartment("Finance");
        int sales = addDepartment("Sales");
        int accountant = addJobTitle(finance, "Accountant");

        createEmployee("a@example.test", sales, accountant)
                .andExpect(status().isBadRequest())
                .andExpect(content().string("The job title \"Accountant\" is not in the Sales department."));
        createEmployee("b@example.test", null, accountant)
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Choose the department before the job title."));

        // Both are optional.
        createEmployee("c@example.test", null, null).andExpect(status().isOk());
        createEmployee("d@example.test", sales, null).andExpect(status().isOk());
    }

    @Test
    void retiredEntriesStayOnTheirEmployeesButCantBeNewlyChosen() throws Exception {
        int finance = addDepartment("Finance");
        int accountant = addJobTitle(finance, "Accountant");
        int clerk = addJobTitle(finance, "Clerk");
        int existing = JsonPath.read(createEmployee("old@example.test", finance, accountant)
                .andReturn().getResponse().getContentAsString(), "$.employeeId");

        mockMvc.perform(post("/api/job-titles/{id}/retire", accountant)).andExpect(status().isOk());

        createEmployee("new@example.test", finance, accountant)
                .andExpect(status().isConflict())
                .andExpect(content().string("The job title \"Accountant\" is retired and can't be chosen."));
        updateEmployee(existing, "old@example.test", finance, accountant)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Accountant"));

        // A retired department takes its job titles out of the choices too.
        mockMvc.perform(post("/api/departments/{id}/retire", finance)).andExpect(status().isOk());
        createEmployee("new@example.test", finance, clerk).andExpect(status().isConflict());
        send(post("/api/departments/{id}/job-titles", finance), "{\"name\": \"Analyst\"}")
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/departments/{id}/restore", finance)).andExpect(status().isOk());
        mockMvc.perform(post("/api/job-titles/{id}/restore", accountant)).andExpect(status().isOk());
        createEmployee("new@example.test", finance, accountant).andExpect(status().isOk());

        assertThat(auditActions()).contains(
                "JOB_TITLE_RETIRED", "DEPARTMENT_RETIRED", "DEPARTMENT_RESTORED", "JOB_TITLE_RESTORED");
        mockMvc.perform(get("/api/departments")).andExpect(jsonPath("$[0].jobTitles[0].active").value(true));
    }

    @Test
    void unknownEntriesAreNotFound() throws Exception {
        createEmployee("x@example.test", 9999, null).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/job-titles/9999/retire")).andExpect(status().isNotFound());
    }

    @Test
    void namesAreCleanedTheSameWayForEveryKindOfEntry() {
        assertThat(ChoiceOption.cleanName("  Human \t Resources ")).isEqualTo("Human Resources");
        assertThatThrownBy(() -> ChoiceOption.cleanName("x".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);

        Department department = new Department("Finance");
        assertThat(department.describe()).isEqualTo("department \"Finance\"");
        assertThat(department.auditPrefix()).isEqualTo("DEPARTMENT");
        assertThat(department.retire()).isTrue();
        assertThat(department.retire()).isFalse();
    }

    // ---------- helpers ----------

    private int addDepartment(String name) throws Exception {
        String body = send(post("/api/departments"), "{\"name\": \"" + name + "\"}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.departmentId");
    }

    private int addJobTitle(int departmentId, String name) throws Exception {
        String body = send(post("/api/departments/{id}/job-titles", departmentId), "{\"name\": \"" + name + "\"}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.jobTitleId");
    }

    private ResultActions createEmployee(String email, Integer departmentId, Integer jobTitleId) throws Exception {
        return send(post("/api/employees"), EMPLOYEE.formatted(email, departmentId, jobTitleId));
    }

    private ResultActions updateEmployee(int id, String email, Integer departmentId, Integer jobTitleId)
            throws Exception {
        return send(put("/api/employees/{id}", id), EMPLOYEE.formatted(email, departmentId, jobTitleId));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private List<String> auditActions() {
        return auditLogRepository.findTop200ByOrderByAuditIdDesc().stream().map(AuditLog::getAction).toList();
    }
}
