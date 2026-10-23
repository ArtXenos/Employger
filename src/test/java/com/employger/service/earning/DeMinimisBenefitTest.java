package com.employger.service.earning;

import com.employger.entity.Earning;
import com.employger.repository.EarningRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.PriorEmployerCompensationRepository;
import com.employger.service.EarningsCalculator;
import com.employger.service.PayrollCalculationService;
import com.employger.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** Each de minimis kind's own period and limit, used through the parent type. */
@ExtendWith(MockitoExtension.class)
class DeMinimisBenefitTest {

    @Mock
    private SystemSettingService settings;

    private PayrollCalculationService rates;
    private Map<Earning.Type, DeMinimisBenefit> benefits;

    @BeforeEach
    void setUp() {
        // No stored settings: every limit falls back to its documented default.
        lenient().when(settings.findSettingValue(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        rates = new PayrollCalculationService(settings);

        benefits = Map.of(
                Earning.Type.DE_MINIMIS_RICE, new RiceSubsidy(rates),
                Earning.Type.DE_MINIMIS_CLOTHING, new ClothingAllowance(rates),
                Earning.Type.DE_MINIMIS_MEDICAL, new MedicalCashAllowance(rates),
                Earning.Type.DE_MINIMIS_LEAVE, new MonetizedLeave(rates));
    }

    @Test
    void eachBenefitReportsTheTypeItHandles() {
        benefits.forEach((type, benefit) -> assertThat(benefit.type()).isEqualTo(type));
    }

    @ParameterizedTest(name = "{0} on {1}: {2} to {3}")
    @CsvSource({
            "DE_MINIMIS_RICE, 2026-02-15, 2026-02-01, 2026-02-28",
            "DE_MINIMIS_RICE, 2028-02-29, 2028-02-01, 2028-02-29",    // leap year
            "DE_MINIMIS_CLOTHING, 2026-10-05, 2026-01-01, 2026-12-31",
            "DE_MINIMIS_MEDICAL, 2026-06-30, 2026-01-01, 2026-06-30", // last day of first half
            "DE_MINIMIS_MEDICAL, 2026-07-01, 2026-07-01, 2026-12-31", // first day of second half
            "DE_MINIMIS_LEAVE, 2026-12-31, 2026-01-01, 2026-12-31",
    })
    void eachBenefitHasItsOwnPeriod(String type, String date, String start, String end) {
        DeMinimisBenefit.ExemptionPeriod period =
                benefits.get(Earning.Type.valueOf(type)).periodContaining(LocalDate.parse(date));

        assertThat(period.start()).isEqualTo(start);
        assertThat(period.end()).isEqualTo(end);
    }

    @ParameterizedTest(name = "{0}: limit {1}")
    @CsvSource({
            "DE_MINIMIS_RICE, 2000",
            "DE_MINIMIS_CLOTHING, 6000",
            "DE_MINIMIS_MEDICAL, 1500",
            "DE_MINIMIS_LEAVE, 9578.50",    // 10 days x 957.85 daily rate
    })
    void eachBenefitHasItsOwnLimit(String type, String expected) {
        assertThat(benefits.get(Earning.Type.valueOf(type)).limit(new BigDecimal("957.85")))
                .isEqualByComparingTo(expected);
    }

    @Test
    void storedSettingOverridesTheDefaultLimit() {
        lenient().when(settings.findSettingValue("de_minimis_rice_monthly")).thenReturn(Optional.of("2500"));

        assertThat(new RiceSubsidy(rates).limit(BigDecimal.ZERO)).isEqualByComparingTo("2500");
    }

    @Test
    void calculatorNeedsExactlyTheDeMinimisTypes() {
        EarningRepository earnings = mock(EarningRepository.class);
        PayrollRepository payroll = mock(PayrollRepository.class);
        PriorEmployerCompensationRepository prior = mock(PriorEmployerCompensationRepository.class);

        List<DeMinimisBenefit> withoutLeave = List.of(
                new RiceSubsidy(rates), new ClothingAllowance(rates), new MedicalCashAllowance(rates));

        assertThatThrownBy(() -> new EarningsCalculator(earnings, payroll, prior, rates, withoutLeave))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No de minimis benefit for DE_MINIMIS_LEAVE");

        List<DeMinimisBenefit> twoRice = List.of(new RiceSubsidy(rates), new RiceSubsidy(rates));

        assertThatThrownBy(() -> new EarningsCalculator(earnings, payroll, prior, rates, twoRice))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("More than one de minimis benefit for DE_MINIMIS_RICE");
    }

    @Test
    void everyEarningTypeHasALabel() {
        for (Earning.Type type : Earning.Type.values()) {
            assertThat(type.getLabel()).isNotBlank();
        }
        assertThat(Earning.Type.DE_MINIMIS_LEAVE.getLabel()).isEqualTo("Monetized unused leave");
    }
}
