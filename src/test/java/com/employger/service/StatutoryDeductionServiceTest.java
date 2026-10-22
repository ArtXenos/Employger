package com.employger.service;

import com.employger.entity.PayCycle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Expected values are taken from the official schedules (verified
 * 2026-10-22): SSS Circular 2024-006, PhilHealth Advisory 2026-0042,
 * Pag-IBIG Circular 460, and BIR RR 11-2018 Annex E.
 */
@ExtendWith(MockitoExtension.class)
class StatutoryDeductionServiceTest {

    private static final Map<String, String> V3_SETTINGS = Map.ofEntries(
            Map.entry("sss_employee_rate", "0.05"),
            Map.entry("sss_msc_minimum", "5000"),
            Map.entry("sss_msc_maximum", "35000"),
            Map.entry("sss_msc_step", "500"),
            Map.entry("philhealth_premium_rate", "0.05"),
            Map.entry("philhealth_salary_floor", "10000"),
            Map.entry("philhealth_salary_ceiling", "100000"),
            Map.entry("philhealth_employee_share", "0.50"),
            Map.entry("pagibig_employee_rate", "0.02"),
            Map.entry("pagibig_low_salary_rate", "0.01"),
            Map.entry("pagibig_low_salary_threshold", "1500"),
            Map.entry("pagibig_max_fund_salary", "10000"));

    private static final Map<String, String> V5_SETTINGS = Map.ofEntries(
            Map.entry("sss_employer_rate", "0.10"),
            Map.entry("sss_ec_msc_threshold", "15000"),
            Map.entry("sss_ec_amount_low", "10"),
            Map.entry("sss_ec_amount_high", "30"),
            Map.entry("pagibig_employer_rate", "0.02"));

    @Mock
    private SystemSettingService systemSettingService;

    private StatutoryDeductionService service;

    @BeforeEach
    void setUp() {
        V3_SETTINGS.forEach((name, value) ->
                lenient().when(systemSettingService.getSettingValue(name)).thenReturn(value));
        V5_SETTINGS.forEach((name, value) ->
                lenient().when(systemSettingService.getSettingValue(name)).thenReturn(value));
        lenient().when(systemSettingService.findSettingValue("statutory_deductions_enabled"))
                .thenReturn(Optional.of("true"));

        service = new StatutoryDeductionService(
                systemSettingService,
                new PayrollCalculationService(systemSettingService));
    }

    // Rows of the SSS Circular 2024-006 table: compensation -> employee total.
    @ParameterizedTest(name = "SSS: salary {0} -> employee {1}")
    @CsvSource({
            "4000.00, 250.00",      // below 5,250 -> MSC 5,000
            "5249.99, 250.00",
            "5250.00, 275.00",      // 5,250 - 5,749.99 -> MSC 5,500
            "5749.99, 275.00",
            "20249.99, 1000.00",    // MSC 20,000 (regular SS only)
            "20250.00, 1025.00",    // MSC 20,500 (20,000 SS + 500 MPF)
            "25000.00, 1250.00",
            "34749.99, 1725.00",
            "34750.00, 1750.00",    // 34,750 and over -> MSC 35,000
            "250000.00, 1750.00",
    })
    void sssEmployeeShareMatchesOfficialTable(String salary, String expected) {
        assertThat(service.monthlySss(new BigDecimal(salary))).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "PhilHealth: salary {0} -> employee {1}")
    @CsvSource({
            "8000.00, 250.00",      // floor 10,000 -> 500 premium, half
            "10000.00, 250.00",
            "25000.00, 625.00",
            "100000.00, 2500.00",
            "150000.00, 2500.00",   // ceiling 100,000 -> 5,000 premium, half
    })
    void philHealthEmployeeShare(String salary, String expected) {
        assertThat(service.monthlyPhilHealth(new BigDecimal(salary))).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "Pag-IBIG: salary {0} -> employee {1}")
    @CsvSource({
            "1500.00, 15.00",       // 1% at or below 1,500
            "1500.01, 30.0002",     // 2% above 1,500 (rounded per period later)
            "10000.00, 200.00",
            "25000.00, 200.00",     // maximum fund salary 10,000
    })
    void pagIbigEmployeeShare(String salary, String expected) {
        assertThat(service.monthlyPagIbig(new BigDecimal(salary))).isEqualByComparingTo(expected);
    }

    // Rows of the SSS Circular 2024-006 table: compensation -> employer
    // regular SS + MPF, and EC.
    @ParameterizedTest(name = "SSS employer: salary {0} -> {1} + EC {2}")
    @CsvSource({
            "4000.00, 500.00, 10.00",       // below 5,250 -> MSC 5,000
            "14749.99, 1450.00, 10.00",     // MSC 14,500: last EC 10 row
            "14750.00, 1500.00, 30.00",     // MSC 15,000: EC becomes 30
            "20250.00, 2050.00, 30.00",     // 2,000 regular + 50 MPF
            "25000.00, 2500.00, 30.00",     // table total 2,530.00
            "34750.00, 3500.00, 30.00",     // table total 3,530.00
            "250000.00, 3500.00, 30.00",
    })
    void sssEmployerShareAndEcMatchOfficialTable(String salary, String employer, String ec) {
        assertThat(service.monthlySssEmployer(new BigDecimal(salary))).isEqualByComparingTo(employer);
        assertThat(service.monthlyEc(new BigDecimal(salary))).isEqualByComparingTo(ec);
    }

    @ParameterizedTest(name = "PhilHealth employer: salary {0} -> {1}")
    @CsvSource({
            "8000.00, 250.00",      // premium 500 at the floor, shared equally
            "25000.00, 625.00",
            "150000.00, 2500.00",   // premium 5,000 at the ceiling
    })
    void philHealthEmployerPaysTheOtherHalf(String salary, String expected) {
        assertThat(service.monthlyPhilHealthEmployer(new BigDecimal(salary))).isEqualByComparingTo(expected);
        assertThat(service.monthlyPhilHealthEmployer(new BigDecimal(salary)))
                .isEqualByComparingTo(service.monthlyPhilHealth(new BigDecimal(salary)));
    }

    @ParameterizedTest(name = "Pag-IBIG employer: salary {0} -> {1}")
    @CsvSource({
            "1500.00, 30.00",       // employer 2% even where the employee pays 1%
            "10000.00, 200.00",
            "25000.00, 200.00",     // maximum fund salary 10,000
    })
    void pagIbigEmployerShare(String salary, String expected) {
        assertThat(service.monthlyPagIbigEmployer(new BigDecimal(salary))).isEqualByComparingTo(expected);
    }

    @Test
    void employerSharesAreSpreadPerCycleAndNotDeducted() {
        StatutoryDeductionService.StatutoryDeductions result = service.calculate(
                new BigDecimal("25000.00"), PayCycle.CycleType.SEMI_MONTHLY, new BigDecimal("12500.00"));

        // Half of each monthly employer amount.
        assertThat(result.employer().sss()).isEqualByComparingTo("1250.00");
        assertThat(result.employer().ec()).isEqualByComparingTo("15.00");
        assertThat(result.employer().philHealth()).isEqualByComparingTo("312.50");
        assertThat(result.employer().pagIbig()).isEqualByComparingTo("100.00");
        assertThat(result.employer().total()).isEqualByComparingTo("1677.50");
        // Employee deductions are exactly as before.
        assertThat(result.totalContributions()).isEqualByComparingTo("1037.50");
    }

    @Test
    void noEmployerSharesWhenDisabled() {
        lenient().when(systemSettingService.findSettingValue("statutory_deductions_enabled"))
                .thenReturn(Optional.of("false"));

        assertThat(service.calculate(new BigDecimal("25000.00"), PayCycle.CycleType.MONTHLY,
                new BigDecimal("25000.00")).employer().total()).isEqualByComparingTo("0");
    }

    // RR 11-2018 annual table for 2023 onwards (year-end annualization).
    @ParameterizedTest(name = "Annual tax: {0} -> {1}")
    @CsvSource({
            "250000.00, 0.00",
            "400000.00, 22500.00",
            "800000.00, 102500.00",
            "2000000.00, 402500.00",
            "8000000.00, 2202500.00",
            "273947.50, 3592.13",       // 15% x 23,947.50 = 3,592.125
            "9000000.00, 2552500.00",   // 2,202,500 + 35% x 1,000,000
    })
    void annualTaxFollowsTheGraduatedTable(String taxable, String expected) {
        assertThat(PhilippineWithholdingTax.forYear(new BigDecimal(taxable))).isEqualByComparingTo(expected);
    }

    // Overtime multipliers by day (guide reviewed 2026-10-23).
    @ParameterizedTest(name = "Overtime {0}, rest day {1} -> {2}")
    @CsvSource({
            "NONE, false, 1.25",
            "NONE, true, 1.69",         // 130% x 130%
            "SPECIAL, false, 1.69",     // 130% x 130%
            "SPECIAL, true, 1.95",      // 150% x 130%
            "REGULAR, false, 2.60",     // 200% x 130%
            "REGULAR, true, 3.38",      // 260% x 130%
            "DOUBLE, false, 3.90",      // 300% x 130%
            "DOUBLE, true, 5.07",       // 390% x 130%
    })
    void overtimeMultiplierDependsOnTheDay(String type, boolean restDay, String expected) {
        lenient().when(systemSettingService.getSettingValue("default_overtime_rate")).thenReturn("1.25");
        PayrollCalculationService rates = new PayrollCalculationService(systemSettingService);

        com.employger.entity.Holiday.Type holidayType =
                type.equals("NONE") ? null : com.employger.entity.Holiday.Type.valueOf(type);

        assertThat(rates.dayRates(holidayType, restDay).overtime()).isEqualByComparingTo(expected);
    }

    // BIR RR 11-2018 Annex E bracket boundaries.
    @ParameterizedTest(name = "Tax {0}: {1} -> {2}")
    @CsvSource({
            "SEMI_MONTHLY, 10417.00, 0.00",
            "SEMI_MONTHLY, 16667.00, 937.50",
            "SEMI_MONTHLY, 20000.00, 1604.10",
            "SEMI_MONTHLY, 33333.00, 4270.70",
            "MONTHLY, 20833.00, 0.00",
            "MONTHLY, 33333.00, 1875.00",
            "MONTHLY, 66667.00, 8541.80",
            "MONTHLY, 700000.00, 195208.35",  // 183,541.80 + 35% x 33,333
            "WEEKLY, 4808.00, 0.00",
            "WEEKLY, 7692.00, 432.60",
            "BI_WEEKLY, 15384.00, 865.20",   // two weekly periods of 7,692
    })
    void withholdingTaxFollowsAnnexE(String cycleType, String compensation, String expected) {
        assertThat(PhilippineWithholdingTax.forPeriod(
                PayCycle.CycleType.valueOf(cycleType), new BigDecimal(compensation)))
                .isEqualByComparingTo(expected);
    }

    @Test
    void semiMonthlyPayrollForMonthlySalaryOf25000() {
        StatutoryDeductionService.StatutoryDeductions result = service.calculate(
                new BigDecimal("25000.00"), PayCycle.CycleType.SEMI_MONTHLY, new BigDecimal("12500.00"));

        // Half of each monthly employee share.
        assertThat(result.sss()).isEqualByComparingTo("625.00");
        assertThat(result.philHealth()).isEqualByComparingTo("312.50");
        assertThat(result.pagIbig()).isEqualByComparingTo("100.00");
        assertThat(result.totalContributions()).isEqualByComparingTo("1037.50");
        // 12,500 - 1,037.50 = 11,462.50; 15% over 10,417 = 156.825 -> 156.83
        assertThat(result.taxableCompensation()).isEqualByComparingTo("11462.50");
        assertThat(result.withholdingTax()).isEqualByComparingTo("156.83");
    }

    @Test
    void monthlyPayrollForMonthlySalaryOf25000() {
        StatutoryDeductionService.StatutoryDeductions result = service.calculate(
                new BigDecimal("25000.00"), PayCycle.CycleType.MONTHLY, new BigDecimal("25000.00"));

        assertThat(result.totalContributions()).isEqualByComparingTo("2075.00");
        // 25,000 - 2,075 = 22,925; 15% over 20,833 = 313.80
        assertThat(result.withholdingTax()).isEqualByComparingTo("313.80");
    }

    @Test
    void minimumWageEarnerPaysContributionsButNoTaxOnRegularAndOvertimePay() {
        StatutoryDeductionService.StatutoryDeductions result = service.calculate(
                new BigDecimal("13000.00"), PayCycle.CycleType.SEMI_MONTHLY,
                new BigDecimal("7000.00"), true, new BigDecimal("7000.00"));

        // SSS MSC 13,000 x 5% / 2 = 325.00; PhilHealth 13,000 x 5% x 50% / 2
        // = 162.50; Pag-IBIG 10,000 x 2% / 2 = 100.00.
        assertThat(result.totalContributions()).isEqualByComparingTo("587.50");
        assertThat(result.taxableCompensation()).isEqualByComparingTo("0.00");
        assertThat(result.withholdingTax()).isEqualByComparingTo("0.00");
        assertThat(result.notes()).anySatisfy(note -> assertThat(note)
                .startsWith("Minimum wage earner").contains("RR 11-2018"));
    }

    @Test
    void minimumWageEarnerIsTaxedOnlyOnOtherCompensation() {
        // 6,500 exempt pay plus 11,500 other compensation (e.g. a bonus).
        StatutoryDeductionService.StatutoryDeductions result = service.calculate(
                new BigDecimal("13000.00"), PayCycle.CycleType.SEMI_MONTHLY,
                new BigDecimal("18000.00"), true, new BigDecimal("6500.00"));

        // Contributions are not subtracted again from the other compensation.
        // 15% x (11,500 - 10,417) = 162.45
        assertThat(result.taxableCompensation()).isEqualByComparingTo("11500.00");
        assertThat(result.withholdingTax()).isEqualByComparingTo("162.45");
    }

    @Test
    void sameIncomeIsTaxedWhenNotMinimumWageEarner() {
        StatutoryDeductionService.StatutoryDeductions result = service.calculate(
                new BigDecimal("25000.00"), PayCycle.CycleType.SEMI_MONTHLY,
                new BigDecimal("12500.00"), false, new BigDecimal("12500.00"));

        // The exempt amount is ignored for everyone else.
        assertThat(result.withholdingTax()).isEqualByComparingTo("156.83");
        assertThat(result.notes()).noneSatisfy(note -> assertThat(note).contains("Minimum wage earner"));
    }

    @Test
    void deductionsAboveGrossPayAreFlagged() {
        StatutoryDeductionService.StatutoryDeductions result = service.calculate(
                new BigDecimal("600.00"), PayCycle.CycleType.WEEKLY, new BigDecimal("100.00"));

        // Weekly shares on a 600 monthly salary: SSS 57.69 + PhilHealth 57.69
        // + Pag-IBIG 1.38 = 116.76, more than the 100.00 gross pay.

        assertThat(result.notes()).anySatisfy(note -> assertThat(note).contains("exceed gross pay"));
    }

    @Test
    void nothingIsDeductedWhenDisabled() {
        lenient().when(systemSettingService.findSettingValue("statutory_deductions_enabled"))
                .thenReturn(Optional.of("false"));

        StatutoryDeductionService.StatutoryDeductions result = service.calculate(
                new BigDecimal("25000.00"), PayCycle.CycleType.MONTHLY, new BigDecimal("25000.00"));

        assertThat(result.totalContributions()).isEqualByComparingTo("0");
        assertThat(result.withholdingTax()).isEqualByComparingTo("0");
        assertThat(result.taxableCompensation()).isEqualByComparingTo("25000.00");
    }
}
