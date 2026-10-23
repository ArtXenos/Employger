package com.employger.service.contribution;

import com.employger.service.PayrollCalculationService;
import com.employger.service.StatutoryDeductionService;
import com.employger.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/**
 * The contributions used through their common parent type. Figures are the
 * official schedules already checked in StatutoryDeductionServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class StatutoryContributionTest {

    private static final Map<String, String> SETTINGS = Map.ofEntries(
            Map.entry("sss_employee_rate", "0.05"),
            Map.entry("sss_employer_rate", "0.10"),
            Map.entry("sss_msc_minimum", "5000"),
            Map.entry("sss_msc_maximum", "35000"),
            Map.entry("sss_msc_step", "500"),
            Map.entry("sss_ec_msc_threshold", "15000"),
            Map.entry("sss_ec_amount_low", "10"),
            Map.entry("sss_ec_amount_high", "30"),
            Map.entry("philhealth_premium_rate", "0.05"),
            Map.entry("philhealth_salary_floor", "10000"),
            Map.entry("philhealth_salary_ceiling", "100000"),
            Map.entry("philhealth_employee_share", "0.50"),
            Map.entry("pagibig_employee_rate", "0.02"),
            Map.entry("pagibig_employer_rate", "0.02"),
            Map.entry("pagibig_low_salary_rate", "0.01"),
            Map.entry("pagibig_low_salary_threshold", "1500"),
            Map.entry("pagibig_max_fund_salary", "10000"));

    @Mock
    private SystemSettingService settings;

    private List<StatutoryContribution> contributions;

    @BeforeEach
    void setUp() {
        SETTINGS.forEach((name, value) -> lenient().when(settings.getSettingValue(name)).thenReturn(value));

        contributions = List.of(
                new SssContribution(settings),
                new PhilHealthContribution(settings),
                new PagIbigContribution(settings));
    }

    @Test
    void eachContributionAnswersTheSameCallWithItsOwnFormula() {
        BigDecimal salary = new BigDecimal("25000.00");

        // type -> employee, employer, EC (SSS Circular 2024-006 row 25,000;
        // PhilHealth 5% shared equally; Pag-IBIG 2% of the 10,000 fund cap).
        Map<StatutoryContribution.Type, String[]> expected = Map.of(
                StatutoryContribution.Type.SSS, new String[] {"1250.00", "2500.00", "30.00"},
                StatutoryContribution.Type.PHILHEALTH, new String[] {"625.00", "625.00", "0"},
                StatutoryContribution.Type.PAG_IBIG, new String[] {"200.00", "200.00", "0"});

        for (StatutoryContribution contribution : contributions) {
            String[] shares = expected.get(contribution.type());

            assertThat(contribution.monthlyEmployeeShare(salary)).isEqualByComparingTo(shares[0]);
            assertThat(contribution.monthlyEmployerShare(salary)).isEqualByComparingTo(shares[1]);
            assertThat(contribution.monthlyEmployeesCompensation(salary)).isEqualByComparingTo(shares[2]);
        }
    }

    @Test
    void onlySssOverridesTheEmployeesCompensationDefault() {
        BigDecimal salary = new BigDecimal("25000.00");

        assertThat(contributions)
                .filteredOn(contribution -> contribution.monthlyEmployeesCompensation(salary).signum() != 0)
                .extracting(StatutoryContribution::type)
                .containsExactly(StatutoryContribution.Type.SSS);
    }

    @ParameterizedTest(name = "SSS MSC: salary {0} -> {1}")
    @CsvSource({
            "0.00, 5000",           // clamped to the minimum
            "5249.99, 5000",
            "5250.00, 5500",
            "34749.99, 34500",
            "34750.00, 35000",
            "1000000.00, 35000",    // clamped to the maximum
    })
    void sssMonthlySalaryCreditIsClampedToTheSchedule(String salary, String expected) {
        assertThat(new SssContribution(settings).monthlySalaryCredit(new BigDecimal(salary)))
                .isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "PhilHealth premium: salary {0} -> {1}")
    @CsvSource({
            "9999.99, 500.00",      // floor 10,000
            "10000.00, 500.00",
            "100000.00, 5000.00",
            "100000.01, 5000.00",   // ceiling 100,000
    })
    void philHealthPremiumStaysWithinFloorAndCeiling(String salary, String expected) {
        assertThat(new PhilHealthContribution(settings).monthlyPremium(new BigDecimal(salary)))
                .isEqualByComparingTo(expected);
    }

    @Test
    void forPeriodSpreadsEveryContributionAndRoundsToCentavos() {
        // Weekly: 12 / 52 of a month. SSS on MSC 5,000: 250 x 12/52 = 57.6923...
        BigDecimal weekly = BigDecimal.valueOf(12).divide(BigDecimal.valueOf(52), 10, RoundingMode.HALF_UP);

        StatutoryContribution.PeriodShares sss = contributions.get(0).forPeriod(new BigDecimal("600.00"), weekly);

        assertThat(sss.employee()).isEqualTo(new BigDecimal("57.69"));
        assertThat(sss.employer()).isEqualTo(new BigDecimal("115.38"));
        assertThat(sss.employeesCompensation()).isEqualTo(new BigDecimal("2.31"));

        // PhilHealth has no EC: the default zero is still a centavo amount.
        assertThat(contributions.get(1).forPeriod(new BigDecimal("600.00"), weekly).employeesCompensation())
                .isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void invalidRateSettingIsReportedClearly() {
        lenient().when(settings.getSettingValue("pagibig_employee_rate")).thenReturn("two percent");

        assertThatThrownBy(() -> new PagIbigContribution(settings).monthlyEmployeeShare(new BigDecimal("5000")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid pagibig_employee_rate setting; expected a number");
    }

    @Test
    void serviceNeedsEachContributionExactlyOnce() {
        PayrollCalculationService rates = new PayrollCalculationService(settings);

        assertThatThrownBy(() -> new StatutoryDeductionService(settings, rates, contributions.subList(0, 2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected exactly one PAG_IBIG contribution, found 0");

        List<StatutoryContribution> twoSss = List.of(
                contributions.get(0), new SssContribution(settings), contributions.get(1), contributions.get(2));

        assertThatThrownBy(() -> new StatutoryDeductionService(settings, rates, twoSss))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected exactly one SSS contribution, found 2");
    }
}
