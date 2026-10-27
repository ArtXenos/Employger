package com.employger.service.salary;

import com.employger.entity.Employee;
import com.employger.entity.Payroll;
import com.employger.entity.PayCycle;
import com.employger.entity.SalaryChange;
import com.employger.repository.PayrollRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalaryTimelineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private final Employee employee = employee();

    @Test
    void withoutChangesTheCurrentSalaryAppliesEveryDay() {
        SalaryTimeline timeline = new SalaryTimeline(new BigDecimal("25000"), List.of());

        assertThat(timeline.salaryOn(LocalDate.of(2020, 1, 1))).isEqualByComparingTo("25000");
        assertThat(timeline.changeDatesWithin(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).isEmpty();
    }

    @Test
    void eachChangeAppliesFromItsEffectiveDate() {
        SalaryTimeline timeline = new SalaryTimeline(new BigDecimal("30000"), List.of(
                change("25000", "27000", "2026-10-08"),
                change("27000", "30000", "2027-01-01")));

        assertThat(timeline.salaryOn(LocalDate.of(2026, 10, 7))).isEqualByComparingTo("25000");
        assertThat(timeline.salaryOn(LocalDate.of(2026, 10, 8))).isEqualByComparingTo("27000");
        assertThat(timeline.salaryOn(LocalDate.of(2026, 12, 31))).isEqualByComparingTo("27000");
        assertThat(timeline.salaryOn(LocalDate.of(2027, 1, 1))).isEqualByComparingTo("30000");
        assertThat(timeline.changeDatesWithin(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .containsExactly(LocalDate.of(2026, 10, 8));
    }

    @Test
    void aChangeOnTheFirstDayOfThePeriodDoesNotSplitIt() {
        SalaryTimeline timeline = new SalaryTimeline(new BigDecimal("27000"), List.of(
                change("25000", "27000", "2026-10-01")));

        assertThat(timeline.changeDatesWithin(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15))).isEmpty();
    }

    @Test
    void theLaterOfTwoChangesOnTheSameDateWins() {
        SalaryTimeline timeline = new SalaryTimeline(new BigDecimal("26000"), List.of(
                change("25000", "28000", "2026-11-01"),
                change("28000", "26000", "2026-11-01")));

        assertThat(timeline.salaryOn(LocalDate.of(2026, 11, 1))).isEqualByComparingTo("26000");
    }

    @Test
    void rulesRefuseDatesBeforeHireOrTooFarAhead() {
        assertThatThrownBy(() -> new NotBeforeHireDate().check(employee, LocalDate.of(2025, 12, 31), TODAY))
                .hasMessageContaining("before the date hired");
        assertThatThrownBy(() -> new NotMoreThanAYearAhead().check(employee, TODAY.plusYears(1).plusDays(1), TODAY))
                .hasMessageContaining("more than a year");
        assertThatCode(() -> new NotMoreThanAYearAhead().check(employee, TODAY.plusYears(1), TODAY))
                .doesNotThrowAnyException();
    }

    @Test
    void ruleRefusesADateInsideProcessedPayrollButNotADraft() {
        PayrollRepository payrollRepository = mock(PayrollRepository.class);
        when(payrollRepository.findByEmployeeEmployeeId(7)).thenReturn(List.of(
                payroll("October 1-15, 2026", "2026-10-15", Payroll.Status.PAID),
                payroll("October 16-31, 2026", "2026-10-31", Payroll.Status.DRAFT)));
        NotIntoProcessedPayroll rule = new NotIntoProcessedPayroll(payrollRepository);

        assertThatThrownBy(() -> rule.check(employee, LocalDate.of(2026, 10, 15), TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payroll up to 2026-10-15");
        assertThatCode(() -> rule.check(employee, LocalDate.of(2026, 10, 16), TODAY))
                .doesNotThrowAnyException();
    }

    @Test
    void aChangeNeedsAPositiveSalaryAndADate() {
        assertThatThrownBy(() -> SalaryChange.record(employee, null, BigDecimal.ZERO, TODAY, null, null))
                .hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> SalaryChange.record(employee, null, BigDecimal.TEN, null, null, null))
                .hasMessageContaining("takes effect");
    }

    private SalaryChange change(String from, String to, String date) {
        return SalaryChange.record(employee, new BigDecimal(from), new BigDecimal(to), LocalDate.parse(date), null, null);
    }

    private static Employee employee() {
        Employee employee = new Employee();
        employee.setEmployeeId(7);
        employee.setDateHired(LocalDate.of(2026, 1, 5));
        return employee;
    }

    private Payroll payroll(String cycleName, String end, Payroll.Status status) {
        PayCycle cycle = new PayCycle();
        cycle.setCycleName(cycleName);
        cycle.setEndDate(LocalDate.parse(end));
        Payroll payroll = new Payroll();
        payroll.setEmployee(employee);
        payroll.setPayCycle(cycle);
        ReflectionTestUtils.setField(payroll, "status", status);
        return payroll;
    }
}
