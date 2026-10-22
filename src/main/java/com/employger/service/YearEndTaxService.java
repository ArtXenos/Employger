package com.employger.service;

import com.employger.entity.Employee;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.employger.entity.PriorEmployerCompensation;
import com.employger.repository.PayrollRepository;
import com.employger.repository.PriorEmployerCompensationRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Year-end tax adjustment (annualization), RR 11-2018 Sec. 2.79(B)(5),
 * steps 1 to 4: on the last payroll of the year, the tax due on the whole
 * year's taxable compensation is compared with the tax already withheld
 * since January. A shortfall is withheld from this payroll; an excess is
 * refunded with it.
 *
 * "Last payroll of the year" is the payroll whose pay cycle ends on
 * December 31. Leavers who end before December are not annualized
 * automatically (there is no separation date yet).
 */
@Service
public class YearEndTaxService {

    private static final MonthDay YEAR_END = MonthDay.of(12, 31);

    public record Result(
            BigDecimal annualTaxableCompensation,
            BigDecimal annualTaxDue,
            BigDecimal withheldBefore,
            BigDecimal taxToWithhold,
            BigDecimal refund,
            List<String> notes) {
    }

    private final PayrollRepository payrollRepository;
    private final PriorEmployerCompensationRepository priorEmployerRepository;

    public YearEndTaxService(
            PayrollRepository payrollRepository,
            PriorEmployerCompensationRepository priorEmployerRepository) {

        this.payrollRepository = payrollRepository;
        this.priorEmployerRepository = priorEmployerRepository;
    }

    public static boolean isYearEndCycle(PayCycle payCycle) {
        return MonthDay.from(payCycle.getEndDate()).equals(YEAR_END);
    }

    /**
     * @param existing                 the payroll being recalculated, or null
     * @param thisPayrollTaxable       taxable compensation of this payroll
     */
    public Result annualize(
            Employee employee,
            PayCycle payCycle,
            Payroll existing,
            BigDecimal thisPayrollTaxable) {

        int year = payCycle.getEndDate().getYear();
        Integer existingId = existing != null ? existing.getPayrollId() : null;
        List<String> notes = new ArrayList<>();

        BigDecimal earlierTaxable = BigDecimal.ZERO;
        BigDecimal withheld = BigDecimal.ZERO;
        int counted = 0;

        for (Payroll earlier : payrollRepository.findByEmployeeEmployeeIdAndPayCycleEndDateBetween(
                employee.getEmployeeId(), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))) {

            if (earlier.getPayrollId().equals(existingId) || earlier.getStatus() == Payroll.Status.DRAFT) {
                continue;
            }

            counted++;

            BigDecimal taxable = earlier.getTaxableCompensation();

            if (taxable == null) {
                // Payroll from before statutory deductions were recorded.
                taxable = nz(earlier.getGrossPay()).subtract(nz(earlier.getDeductionAmount())).max(BigDecimal.ZERO);
                notes.add("Payroll #" + earlier.getPayrollId() + " has no taxable compensation on record; "
                        + "gross pay less deductions (" + money(taxable) + ") was used.");
            }

            earlierTaxable = earlierTaxable.add(taxable);
            withheld = withheld.add(nz(earlier.getTaxAmount())).subtract(nz(earlier.getTaxRefund()));
        }

        Optional<PriorEmployerCompensation> prior =
                priorEmployerRepository.findByEmployeeEmployeeIdAndTaxYear(employee.getEmployeeId(), year);

        BigDecimal priorTaxable = prior.map(PriorEmployerCompensation::getTaxableCompensation).orElse(BigDecimal.ZERO);
        BigDecimal priorWithheld = prior.map(PriorEmployerCompensation::getTaxWithheld).orElse(BigDecimal.ZERO);

        BigDecimal annualTaxable = earlierTaxable.add(nz(thisPayrollTaxable)).add(priorTaxable);
        BigDecimal annualTax = PhilippineWithholdingTax.forYear(annualTaxable);
        BigDecimal withheldBefore = withheld.add(priorWithheld);
        BigDecimal variance = annualTax.subtract(withheldBefore);

        BigDecimal taxToWithhold = variance.max(BigDecimal.ZERO);
        BigDecimal refund = variance.negate().max(BigDecimal.ZERO);

        notes.add("Year-end adjustment for " + year + ": annual taxable compensation "
                + money(annualTaxable) + " (" + counted + " earlier payroll"
                + (prior.isPresent() ? ", previous employer " + prior.get().getEmployerName() : "")
                + "), tax due " + money(annualTax) + ", already withheld " + money(withheldBefore)
                + (refund.signum() > 0
                        ? "; refund " + money(refund) + "."
                        : "; withhold " + money(taxToWithhold) + " now."));

        return new Result(
                money(annualTaxable),
                money(annualTax),
                money(withheldBefore),
                money(taxToWithhold),
                money(refund),
                notes);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(PayrollCalculationService.FINAL_MONETARY_SCALE, RoundingMode.HALF_UP);
    }
}
