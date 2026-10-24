package com.employger;

import com.employger.entity.Attendance;
import com.employger.entity.Employee;
import com.employger.entity.Holiday;
import com.employger.entity.LeaveRequest;
import com.employger.entity.User;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.HolidayRepository;
import com.employger.repository.LeaveRequestRepository;
import com.employger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Employees file leave on /api/me/leave; HR decides on /api/leave. Dates are
 * in November 2026: the 2nd is a Monday.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "leave.user", roles = "EMPLOYEE")
class LeaveRequestIntegrationTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 2);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private HolidayRepository holidayRepository;
    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setFirstName("Lea");
        employee.setLastName("Ve");
        employee.setEmail("leave@example.test");
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setDateHired(LocalDate.of(2026, 1, 1));
        employee.setBaseSalary(new BigDecimal("25000.00"));
        employee = employeeRepository.save(employee);

        login("leave.user", User.Role.EMPLOYEE, employee);
        login("hr", User.Role.HR_MANAGER, null);
    }

    @Test
    void approvedPaidLeaveMarksEachWorkingDayAsLeave() throws Exception {
        // Friday to Monday: the weekend isn't counted.
        Integer id = file("PAID", MON.plusDays(4), MON.plusDays(7))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workingDays").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturnId();

        decide(id, "APPROVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("hr"));

        assertThat(attendanceOn(MON.plusDays(4)).getStatus()).isEqualTo(Attendance.Status.LEAVE);
        assertThat(attendanceOn(MON.plusDays(4)).getRemarks()).isEqualTo("Paid leave #" + id);
        assertThat(attendanceOn(MON.plusDays(7)).getStatus()).isEqualTo(Attendance.Status.LEAVE);
        assertThat(attendanceRepository.findByEmployeeEmployeeIdAndAttendanceDate(
                employee.getEmployeeId(), MON.plusDays(5))).isEmpty();
    }

    @Test
    void approvedUnpaidLeaveMarksDaysAbsentSoTheyAreDeducted() throws Exception {
        Integer id = file("UNPAID", MON, MON).andExpect(status().isCreated()).andReturnId();

        decide(id, "APPROVED").andExpect(status().isOk());

        assertThat(attendanceOn(MON).getStatus()).isEqualTo(Attendance.Status.ABSENT);
    }

    @Test
    void rejectedLeaveChangesNoAttendance() throws Exception {
        Integer id = file("PAID", MON, MON).andExpect(status().isCreated()).andReturnId();

        decide(id, "REJECTED").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        decide(id, "APPROVED")
                .andExpect(status().isConflict())
                .andExpect(content().string("Only pending leave can be approved or rejected"));

        assertThat(attendanceRepository.findByEmployeeEmployeeIdAndAttendanceDate(
                employee.getEmployeeId(), MON)).isEmpty();
    }

    @Test
    void paidLeaveIsLimitedToFiveDaysAYear() throws Exception {
        file("PAID", MON, MON.plusDays(4)).andExpect(status().isCreated());

        file("PAID", MON.plusDays(7), MON.plusDays(7))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("limited to 5 days in 2026")))
                .andExpect(content().string(containsString("File the rest as unpaid leave")));

        file("UNPAID", MON.plusDays(7), MON.plusDays(7)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/me/leave"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidLeave.allowed").value(5))
                .andExpect(jsonPath("$.paidLeave.pending").value(5))
                .andExpect(jsonPath("$.paidLeave.remaining").value(0))
                .andExpect(jsonPath("$.requests.length()").value(2));
    }

    @Test
    void holidaysAndWeekendsAreNotLeaveDays() throws Exception {
        Holiday holiday = new Holiday();
        holiday.setHolidayDate(MON.plusDays(1));
        holiday.setName("Test holiday");
        holiday.setHolidayType(Holiday.Type.SPECIAL);
        holidayRepository.save(holiday);

        file("PAID", MON, MON.plusDays(2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workingDays").value(2));

        file("PAID", MON.minusDays(2), MON.minusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("no working days")));
    }

    @Test
    void overlappingLeaveAWorkedDayAndBackwardDatesAreRefused() throws Exception {
        file("PAID", MON, MON.plusDays(1)).andExpect(status().isCreated());
        file("UNPAID", MON.plusDays(1), MON.plusDays(2))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("already have pending or approved leave")));

        Attendance worked = new Attendance();
        worked.setEmployee(employee);
        worked.setAttendanceDate(MON.plusDays(3));
        worked.setRegularHours(new BigDecimal("8.00"));
        worked.setStatus(Attendance.Status.PRESENT);
        attendanceRepository.save(worked);

        file("PAID", MON.plusDays(3), MON.plusDays(3))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("worked that day")));

        file("PAID", MON.plusDays(4), MON)
                .andExpect(status().isBadRequest())
                .andExpect(content().string("The end date cannot be before the start date."));
    }

    @Test
    void employeesCanCancelOnlyTheirOwnPendingLeave() throws Exception {
        Integer pending = file("PAID", MON, MON).andExpect(status().isCreated()).andReturnId();
        Integer approved = file("PAID", MON.plusDays(1), MON.plusDays(1)).andExpect(status().isCreated()).andReturnId();
        decide(approved, "APPROVED").andExpect(status().isOk());

        mockMvc.perform(delete("/api/me/leave/{id}", approved)).andExpect(status().isConflict());
        mockMvc.perform(delete("/api/me/leave/{id}", pending)).andExpect(status().isNoContent());

        assertThat(leaveRequestRepository.findById(pending)).isEmpty();
    }

    @Test
    void onlyAdministratorsAndHrDecideWhileGeneralManagersMayRead() throws Exception {
        Integer id = file("PAID", MON, MON).andExpect(status().isCreated()).andReturnId();

        mockMvc.perform(put("/api/leave/{id}/status", id).param("status", "APPROVED"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/leave").param("status", "PENDING").with(user("gm").roles("GENERAL_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leaveId").value(id))
                .andExpect(jsonPath("$[0].employeeName").value("Lea Ve"));

        mockMvc.perform(put("/api/leave/{id}/status", id).param("status", "APPROVED")
                        .with(user("gm").roles("GENERAL_MANAGER")))
                .andExpect(status().isForbidden());

        assertThat(leaveRequestRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(LeaveRequest.Status.PENDING);
    }

    // ---------- helpers ----------

    private IdResult file(String type, LocalDate start, LocalDate end) throws Exception {
        return new IdResult(mockMvc.perform(post("/api/me/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"leaveType\": \"" + type + "\", \"startDate\": \"" + start
                        + "\", \"endDate\": \"" + end + "\", \"reason\": \"Family matter\"}")));
    }

    private ResultActions decide(Integer id, String decision) throws Exception {
        return mockMvc.perform(put("/api/leave/{id}/status", id)
                .param("status", decision)
                .with(user("hr").roles("HR_MANAGER")));
    }

    private Attendance attendanceOn(LocalDate date) {
        return attendanceRepository.findByEmployeeEmployeeIdAndAttendanceDate(employee.getEmployeeId(), date)
                .orElseThrow();
    }

    private void login(String username, User.Role role, Employee linked) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash("not-used");
        user.setRole(role);
        user.setEmployee(linked);
        userRepository.save(user);
    }

    /** ResultActions plus a way to read the new leave's ID. */
    private record IdResult(ResultActions actions) {

        IdResult andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            actions.andExpect(matcher);
            return this;
        }

        Integer andReturnId() throws Exception {
            return com.jayway.jsonpath.JsonPath.read(
                    actions.andReturn().getResponse().getContentAsString(), "$.leaveId");
        }
    }
}
