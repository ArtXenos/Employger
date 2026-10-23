package com.employger.service.earning;

import com.employger.entity.Earning;
import com.employger.service.PayrollCalculationService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Rice subsidy: exempt up to a monthly limit. */
@Component
public class RiceSubsidy extends DeMinimisBenefit {

    public RiceSubsidy(PayrollCalculationService rates) {
        super(rates);
    }

    @Override
    public Earning.Type type() {
        return Earning.Type.DE_MINIMIS_RICE;
    }

    @Override
    public ExemptionPeriod periodContaining(LocalDate date) {
        return new ExemptionPeriod(date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()));
    }

    @Override
    public BigDecimal limit(BigDecimal dailyRate) {
        return setting("de_minimis_rice_monthly", "2000");
    }
}
