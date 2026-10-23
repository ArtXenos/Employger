package com.employger.service.earning;

import com.employger.entity.Earning;
import com.employger.service.PayrollCalculationService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Medical cash allowance to dependents: exempt up to a limit per half-year. */
@Component
public class MedicalCashAllowance extends DeMinimisBenefit {

    public MedicalCashAllowance(PayrollCalculationService rates) {
        super(rates);
    }

    @Override
    public Earning.Type type() {
        return Earning.Type.DE_MINIMIS_MEDICAL;
    }

    /** January to June, or July to December. */
    @Override
    public ExemptionPeriod periodContaining(LocalDate date) {
        boolean firstHalf = date.getMonthValue() <= 6;
        return new ExemptionPeriod(
                LocalDate.of(date.getYear(), firstHalf ? 1 : 7, 1),
                firstHalf ? LocalDate.of(date.getYear(), 6, 30) : LocalDate.of(date.getYear(), 12, 31));
    }

    @Override
    public BigDecimal limit(BigDecimal dailyRate) {
        return setting("de_minimis_medical_semester", "1500");
    }
}
