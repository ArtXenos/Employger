package com.employger.service.earning;

import com.employger.entity.Earning;
import com.employger.service.PayrollCalculationService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Uniform and clothing allowance: exempt up to a yearly limit. */
@Component
public class ClothingAllowance extends DeMinimisBenefit {

    public ClothingAllowance(PayrollCalculationService rates) {
        super(rates);
    }

    @Override
    public Earning.Type type() {
        return Earning.Type.DE_MINIMIS_CLOTHING;
    }

    @Override
    public ExemptionPeriod periodContaining(LocalDate date) {
        return calendarYear(date);
    }

    @Override
    public BigDecimal limit(BigDecimal dailyRate) {
        return setting("de_minimis_clothing_annual", "6000");
    }
}
