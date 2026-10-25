package com.employger.service;

import com.employger.dto.PayrollAdjustmentRequest;
import com.employger.entity.OverpaymentRecovery;
import com.employger.entity.Payroll;
import com.employger.entity.PayrollAdjustment;
import com.employger.entity.Underpayment;
import com.employger.exception.ResourceNotFoundException;
import com.employger.repository.PayrollAdjustmentRepository;
import com.employger.repository.PayrollRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Corrections to PAID payroll (V12). HR records them; the payroll whose
 * cycle contains the apply date settles them, and that payroll still needs
 * the General Manager's sign-off before it can be paid. Once that payroll
 * is processed, the correction is final.
 */
@Service
public class PayrollAdjustmentService {

    private final PayrollAdjustmentRepository adjustmentRepository;
    private final PayrollRepository payrollRepository;
    private final AuditService auditService;

    public PayrollAdjustmentService(
            PayrollAdjustmentRepository adjustmentRepository,
            PayrollRepository payrollRepository,
            AuditService auditService) {

        this.adjustmentRepository = adjustmentRepository;
        this.payrollRepository = payrollRepository;
        this.auditService = auditService;
    }

    public List<PayrollAdjustment> getAll() {
        return adjustmentRepository.findAllByOrderByApplyDateDescAdjustmentIdDesc();
    }

    /** Corrections of one PAID payroll, e.g. to show next to it. */
    public List<PayrollAdjustment> getForCorrectedPayroll(Integer payrollId) {
        return adjustmentRepository.findByCorrectedPayrollPayrollId(payrollId);
    }

    @Transactional
    public PayrollAdjustment create(PayrollAdjustmentRequest request) {

        Payroll corrected = correctedPayroll(request.correctedPayrollId());

        // Which class is made decides the direction and the rules.
        PayrollAdjustment adjustment = switch (request.kind()) {
            case UNDERPAYMENT -> new Underpayment(corrected, request.amount(), request.reason(),
                    request.applyDate(), auditService.currentUser().orElse(null));
            case OVERPAYMENT -> new OverpaymentRecovery(corrected, request.amount(), request.reason(),
                    request.applyDate(), request.employeeConsent(), auditService.currentUser().orElse(null));
        };

        // After the object's own checks, whose messages are more specific.
        ensureDateOpen(corrected, request.applyDate());

        PayrollAdjustment saved = adjustmentRepository.save(adjustment);

        auditService.record("PAYROLL_ADJUSTMENT_CREATED", "Recorded " + saved.describe()
                + ", settled in the cycle containing " + saved.getApplyDate());

        return saved;
    }

    @Transactional
    public PayrollAdjustment update(Integer id, PayrollAdjustmentRequest request) {

        PayrollAdjustment adjustment = find(id);

        if (adjustment.isRecovery() != (request.kind() == PayrollAdjustmentRequest.Kind.OVERPAYMENT)) {
            throw new IllegalArgumentException("An underpayment can't become an overpayment or the other way "
                    + "round. Delete this correction and record a new one.");
        }

        Payroll corrected = correctedPayroll(request.correctedPayrollId());

        String before = adjustment.describe();

        // On failure the transaction rolls the change back.
        adjustment.change(corrected, request.amount(), request.reason(),
                request.applyDate(), request.employeeConsent());
        ensureDateOpen(corrected, request.applyDate());

        // A DRAFT payroll that held it picks it up again, or not, when it
        // is next calculated; processing always recalculates.
        adjustment.release();

        auditService.record("PAYROLL_ADJUSTMENT_UPDATED", "Changed " + before + " to " + adjustment.describe()
                + ", settled in the cycle containing " + adjustment.getApplyDate());

        return adjustment;
    }

    @Transactional
    public void delete(Integer id) {

        PayrollAdjustment adjustment = find(id);
        adjustment.checkNotLocked();

        auditService.record("PAYROLL_ADJUSTMENT_DELETED", "Deleted " + adjustment.describe());

        adjustmentRepository.delete(adjustment);
    }

    private PayrollAdjustment find(Integer id) {
        return adjustmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Correction not found"));
    }

    private Payroll correctedPayroll(Integer payrollId) {
        return payrollRepository.findById(payrollId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll not found"));
    }

    private void ensureDateOpen(Payroll corrected, LocalDate applyDate) {
        if (applyDate != null && payrollRepository.isDateLockedForEmployee(
                corrected.getEmployee().getEmployeeId(), applyDate)) {
            throw new IllegalStateException("Payroll covering " + applyDate + " has already been processed or "
                    + "paid for this employee. Choose a date in a later, open pay cycle.");
        }
    }
}
