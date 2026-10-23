package com.employger.service.metric;

import com.employger.entity.Payroll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Each metric's share of one payroll, and totals through the parent type. */
class PayrollMetricTest {

    private final GrossPayMetric gross = new GrossPayMetric();
    private final OvertimePayMetric overtime = new OvertimePayMetric();
    private final EmployerContributionsMetric employer = new EmployerContributionsMetric();
    private final LaborCostMetric laborCost = new LaborCostMetric(gross, employer);

    @Test
    void eachMetricTakesItsOwnPartOfAPayroll() {
        Payroll payroll = payroll("12500.00", "359.20", true);

        assertThat(gross.amountOf(payroll)).isEqualByComparingTo("12500.00");
        assertThat(overtime.amountOf(payroll)).isEqualByComparingTo("359.20");
        // SSS 1,250 + EC 15 + PhilHealth 312.50 + Pag-IBIG 100
        assertThat(employer.amountOf(payroll)).isEqualByComparingTo("1677.50");
        assertThat(laborCost.amountOf(payroll)).isEqualByComparingTo("14177.50");
    }

    @Test
    void payrollSavedBeforeV5CountsAsNoEmployerShares() {
        Payroll old = payroll("12212.64", null, false);

        assertThat(employer.amountOf(old)).isEqualByComparingTo("0");
        assertThat(overtime.amountOf(old)).isEqualByComparingTo("0");
        assertThat(laborCost.amountOf(old)).isEqualByComparingTo("12212.64");
    }

    @Test
    void totalsAreTheSameCallForEveryMetric() {
        List<Payroll> payrolls = List.of(payroll("12500.00", "359.20", true), payroll("12212.64", null, false));
        List<PayrollMetric> metrics = List.of(gross, overtime, employer, laborCost);

        assertThat(metrics)
                .extracting(metric -> metric.total(payrolls))
                .containsExactly(
                        new BigDecimal("24712.64"),
                        new BigDecimal("359.20"),
                        new BigDecimal("1677.50"),
                        new BigDecimal("26390.14"));
    }

    @Test
    void totalOfNoPayrollIsZeroCentavos() {
        assertThat(laborCost.total(List.of())).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void keysAreUniqueAndStable() {
        assertThat(List.of(gross, overtime, employer, laborCost))
                .extracting(PayrollMetric::key)
                .containsExactly("grossPay", "overtimePay", "employerContributions", "laborCost");
    }

    private static Payroll payroll(String grossPay, String overtimePay, boolean withEmployerShares) {
        Payroll payroll = new Payroll();
        payroll.setGrossPay(new BigDecimal(grossPay));
        payroll.setOvertimePay(overtimePay != null ? new BigDecimal(overtimePay) : null);

        if (withEmployerShares) {
            payroll.setSssEmployerContribution(new BigDecimal("1250.00"));
            payroll.setSssEcContribution(new BigDecimal("15.00"));
            payroll.setPhilHealthEmployerContribution(new BigDecimal("312.50"));
            payroll.setPagIbigEmployerContribution(new BigDecimal("100.00"));
        }

        return payroll;
    }
}
