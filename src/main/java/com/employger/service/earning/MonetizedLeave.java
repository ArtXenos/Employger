package com.employger.service.earning;

import com.employger.entity.Earning;
import com.employger.service.PayrollCalculationService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Monetized unused vacation leave: exempt up to a number of days' pay per year. */
@Component
public class MonetizedLeave extends DeMinimisBenefit {

    public MonetizedLeave(PayrollCalculationService rates) {
        super(rates);
    }

    @Override
    public Earning.Type type() {
        return Earning.Type.DE_MINIMIS_LEAVE;
    }

    @Override
    public ExemptionPeriod periodContaining(LocalDate date) {
        return calendarYear(date);
    }

    /** The only limit measured in days: days x the employee's daily rate. */
    @Override
    public BigDecimal limit(BigDecimal dailyRate) {
        return setting("de_minimis_leave_days", "10").multiply(dailyRate);
    }
}
