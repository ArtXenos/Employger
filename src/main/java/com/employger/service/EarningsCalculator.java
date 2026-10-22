package com.employger.service;

import com.employger.entity.Earning;
import com.employger.entity.Employee;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.employger.repository.EarningRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.PriorEmployerCompensationRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Earnings entries (13th month, bonuses, hazard pay, allowances, de minimis)
 * paid by one payroll, and how they are taxed. Rules (RR 11-2018, Sec.
 * 2.78.1(A)(3) and (B)(11); docs/STATUTORY_DEDUCTIONS.md):
 *
 *  - De minimis entries are exempt up to their limit per period (rice per
 *    month, clothing per year, medical per half-year, monetized leave per
 *    year). The limit is shared by all entries of that kind in the period,
 *    taken in date order. Anything above it counts as an "other benefit".
 *  - 13th month pay, other benefits and that de minimis excess are exempt
 *    up to the benefits_exemption_cap (90,000) per calendar year, counting
 *    what earlier processed or paid payroll and a previous employer already
 *    used. The rest is taxable.
 *  - Hazard pay and taxable allowances are taxable (hazard pay is exempt for
 *    minimum wage earners; PayrollService handles that).
 */
@Service
public class EarningsCalculator {

    public record Result(
            List<Earning> entries,
            BigDecimal hazardPay,
            BigDecimal thirteenthMonthPay,
            BigDecimal otherBenefitsPay,
            BigDecimal deMinimisPay,
            BigDecimal taxableAllowancePay,
            BigDecimal deMinimisExempt,
            BigDecimal exemptBenefits,
            BigDecimal taxableBenefits,
            List<String> notes) {

        public BigDecimal total() {
            return hazardPay.add(thirteenthMonthPay).add(otherBenefitsPay)
                    .add(deMinimisPay).add(taxableAllowancePay);
        }

        /** Earnings that are not taxed for anyone. */
        public BigDecimal nonTaxable() {
            return deMinimisExempt.add(exemptBenefits);
        }
    }

    private final EarningRepository earningRepository;
    private final PayrollRepository payrollRepository;
    private final PriorEmployerCompensationRepository priorEmployerRepository;
    private final PayrollCalculationService rates;

    public EarningsCalculator(
            EarningRepository earningRepository,
            PayrollRepository payrollRepository,
            PriorEmployerCompensationRepository priorEmployerRepository,
            PayrollCalculationService rates) {

        this.earningRepository = earningRepository;
        this.payrollRepository = payrollRepository;
        this.priorEmployerRepository = priorEmployerRepository;
        this.rates = rates;
    }

    /**
     * @param existing    the payroll being recalculated, or null
     * @param dailyRate   the employee's daily rate (for monetized leave)
     */
    public Result calculate(Employee employee, PayCycle payCycle, Payroll existing, BigDecimal dailyRate) {

        Integer existingId = existing != null ? existing.getPayrollId() : null;

        List<Earning> entries = earningRepository
                .findByEmployeeEmployeeIdAndDateGivenBetween(
                        employee.getEmployeeId(), payCycle.getStartDate(), payCycle.getEndDate())
                .stream()
                .filter(entry -> entry.getPayroll() == null
                        || entry.getPayroll().getPayrollId().equals(existingId))
                .sorted(Comparator.comparing(Earning::getDateGiven).thenComparing(Earning::getEarningId))
                .toList();

        List<String> notes = new ArrayList<>();

        BigDecimal hazard = sum(entries, Earning.Type.HAZARD_PAY);
        BigDecimal thirteenth = sum(entries, Earning.Type.THIRTEENTH_MONTH);
        BigDecimal other = sum(entries, Earning.Type.OTHER_BENEFIT);
        BigDecimal allowance = sum(entries, Earning.Type.TAXABLE_ALLOWANCE);

        BigDecimal deMinimis = BigDecimal.ZERO;
        BigDecimal deMinimisExempt = BigDecimal.ZERO;

        for (Earning entry : entries) {
            if (entry.getEarningType().isDeMinimis()) {
                BigDecimal exempt = deMinimisExemptPart(entry, dailyRate);
                deMinimis = deMinimis.add(entry.getAmount());
                deMinimisExempt = deMinimisExempt.add(exempt);

                if (exempt.compareTo(entry.getAmount()) < 0) {
                    notes.add(label(entry.getEarningType()) + " of " + money(entry.getAmount())
                            + " on " + entry.getDateGiven() + " exceeds the de minimis limit; "
                            + money(entry.getAmount().subtract(exempt))
                            + " is treated as an other benefit (90,000 cap).");
                }
            }
        }

        BigDecimal deMinimisExcess = deMinimis.subtract(deMinimisExempt);

        // ---------- 90,000 cap on 13th month and other benefits ----------

        BigDecimal subjectToCap = thirteenth.add(other).add(deMinimisExcess);
        BigDecimal exemptBenefits = BigDecimal.ZERO;
        BigDecimal taxableBenefits = BigDecimal.ZERO;

        if (subjectToCap.signum() > 0) {

            int year = payCycle.getEndDate().getYear();
            BigDecimal cap = rates.decimalSetting("benefits_exemption_cap", "90000");
            BigDecimal used = capUsedBefore(employee, year, existingId);
            BigDecimal remaining = cap.subtract(used).max(BigDecimal.ZERO);

            exemptBenefits = subjectToCap.min(remaining);
            taxableBenefits = subjectToCap.subtract(exemptBenefits);

            if (taxableBenefits.signum() > 0) {
                notes.add("13th month pay and other benefits for " + year + " exceed the "
                        + money(cap) + " exemption (" + money(used) + " already used): "
                        + money(taxableBenefits) + " is taxable.");
            }
        }

        return new Result(
                entries,
                money(hazard),
                money(thirteenth),
                money(other),
                money(deMinimis),
                money(allowance),
                money(deMinimisExempt),
                money(exemptBenefits),
                money(taxableBenefits),
                notes);
    }

    /**
     * The exempt part of one de minimis entry: its share of the limit for
     * its period, after earlier entries of the same kind in that period.
     */
    BigDecimal deMinimisExemptPart(Earning entry, BigDecimal dailyRate) {

        LocalDate date = entry.getDateGiven();
        LocalDate periodStart;
        LocalDate periodEnd;
        BigDecimal limit;

        switch (entry.getEarningType()) {
            case DE_MINIMIS_RICE -> {
                periodStart = date.withDayOfMonth(1);
                periodEnd = date.withDayOfMonth(date.lengthOfMonth());
                limit = rates.decimalSetting("de_minimis_rice_monthly", "2000");
            }
            case DE_MINIMIS_CLOTHING -> {
                periodStart = date.withDayOfYear(1);
                periodEnd = date.withMonth(12).withDayOfMonth(31);
                limit = rates.decimalSetting("de_minimis_clothing_annual", "6000");
            }
            case DE_MINIMIS_MEDICAL -> {
                boolean firstHalf = date.getMonthValue() <= 6;
                periodStart = LocalDate.of(date.getYear(), firstHalf ? 1 : 7, 1);
                periodEnd = firstHalf ? LocalDate.of(date.getYear(), 6, 30) : LocalDate.of(date.getYear(), 12, 31);
                limit = rates.decimalSetting("de_minimis_medical_semester", "1500");
            }
            case DE_MINIMIS_LEAVE -> {
                periodStart = date.withDayOfYear(1);
                periodEnd = date.withMonth(12).withDayOfMonth(31);
                limit = rates.decimalSetting("de_minimis_leave_days", "10").multiply(dailyRate);
            }
            default -> {
                return BigDecimal.ZERO;
            }
        }

        BigDecimal usedBefore = earningRepository
                .findByEmployeeEmployeeIdAndEarningTypeAndDateGivenBetween(
                        entry.getEmployee().getEmployeeId(), entry.getEarningType(), periodStart, periodEnd)
                .stream()
                .filter(other -> isBefore(other, entry))
                .map(Earning::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remaining = limit.subtract(usedBefore).max(BigDecimal.ZERO);

        return entry.getAmount().min(remaining);
    }

    /** Exemption already used this year by other processed or paid payroll and a previous employer. */
    private BigDecimal capUsedBefore(Employee employee, int year, Integer excludedPayrollId) {

        BigDecimal fromPayroll = payrollRepository
                .findByEmployeeEmployeeIdAndPayCycleEndDateBetween(
                        employee.getEmployeeId(), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream()
                .filter(payroll -> !payroll.getPayrollId().equals(excludedPayrollId))
                .filter(payroll -> payroll.getStatus() != Payroll.Status.DRAFT)
                .map(Payroll::getExemptBenefits)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal fromPriorEmployer = priorEmployerRepository
                .findByEmployeeEmployeeIdAndTaxYear(employee.getEmployeeId(), year)
                .map(prior -> prior.getExemptBenefits())
                .orElse(BigDecimal.ZERO);

        return fromPayroll.add(fromPriorEmployer);
    }

    private static boolean isBefore(Earning other, Earning entry) {
        int byDate = other.getDateGiven().compareTo(entry.getDateGiven());
        return byDate < 0 || (byDate == 0 && other.getEarningId() < entry.getEarningId());
    }

    private static BigDecimal sum(List<Earning> entries, Earning.Type type) {
        return entries.stream()
                .filter(entry -> entry.getEarningType() == type)
                .map(Earning::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static String label(Earning.Type type) {
        return switch (type) {
            case THIRTEENTH_MONTH -> "13th month pay";
            case OTHER_BENEFIT -> "Other benefit";
            case HAZARD_PAY -> "Hazard pay";
            case TAXABLE_ALLOWANCE -> "Taxable allowance";
            case DE_MINIMIS_RICE -> "Rice subsidy";
            case DE_MINIMIS_CLOTHING -> "Clothing allowance";
            case DE_MINIMIS_MEDICAL -> "Medical cash allowance";
            case DE_MINIMIS_LEAVE -> "Monetized unused leave";
        };
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(PayrollCalculationService.FINAL_MONETARY_SCALE, RoundingMode.HALF_UP);
    }
}
