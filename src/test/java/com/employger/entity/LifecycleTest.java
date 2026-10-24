package com.employger.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Domain methods that guard an entity's own state: payroll and overtime lifecycles, employee contact details. */
class LifecycleTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 9, 0);

    private final User hr = new User();
    private final User gm = new User();

    // ---------- payroll: DRAFT -> PROCESSED -> APPROVED -> PAID ----------

    @Test
    void draftPayrollCanBeProcessedThenPaid() {
        Payroll payroll = new Payroll();
        assertThat(payroll.isDraft()).isTrue();

        payroll.process(hr, NOW);

        assertThat(payroll.getStatus()).isEqualTo(Payroll.Status.PROCESSED);
        assertThat(payroll.getProcessedBy()).isSameAs(hr);
        assertThat(payroll.getProcessedAt()).isEqualTo(NOW);
        assertThat(payroll.isDraft()).isFalse();

        payroll.approve(gm, NOW.plusHours(2));

        assertThat(payroll.getStatus()).isEqualTo(Payroll.Status.APPROVED);
        assertThat(payroll.getApprovedBy()).isSameAs(gm);
        assertThat(payroll.getApprovedAt()).isEqualTo(NOW.plusHours(2));

        payroll.markPaid("Bank transfer", "REF-1", NOW.plusDays(1));

        assertThat(payroll.getStatus()).isEqualTo(Payroll.Status.PAID);
        assertThat(payroll.getPaymentMethod()).isEqualTo("Bank transfer");
        assertThat(payroll.getPaymentReference()).isEqualTo("REF-1");
        assertThat(payroll.getPaymentDate()).isEqualTo(NOW.plusDays(1));
    }

    @ParameterizedTest
    @EnumSource(value = Payroll.Status.class, names = {"PROCESSED", "APPROVED", "PAID"})
    void onlyDraftPayrollCanBeProcessed(Payroll.Status status) {
        Payroll payroll = new Payroll();
        ReflectionTestUtils.setField(payroll, "status", status);

        assertThatThrownBy(() -> payroll.process(hr, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only DRAFT payroll can be processed");
        assertThat(payroll.getStatus()).isEqualTo(status);
        assertThat(payroll.getProcessedBy()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = Payroll.Status.class, names = {"DRAFT", "PAID"})
    void onlyApprovedPayrollCanBePaid(Payroll.Status status) {
        Payroll payroll = new Payroll();
        ReflectionTestUtils.setField(payroll, "status", status);

        assertThatThrownBy(() -> payroll.markPaid("Cash", null, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only APPROVED payroll can be marked as paid");
        assertThat(payroll.getStatus()).isEqualTo(status);
        assertThat(payroll.getPaymentMethod()).isNull();
    }

    @Test
    void processedPayrollNeedsGeneralManagerApprovalBeforePayment() {
        Payroll payroll = new Payroll();
        payroll.process(hr, NOW);

        assertThatThrownBy(() -> payroll.markPaid("Cash", null, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be approved by the General Manager");
    }

    @Test
    void whoeverProcessedThePayrollCannotApproveIt() {
        Payroll payroll = new Payroll();
        payroll.process(hr, NOW);

        assertThatThrownBy(() -> payroll.approve(hr, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You processed this payroll, so someone else must approve or send it back.");
        assertThat(payroll.getStatus()).isEqualTo(Payroll.Status.PROCESSED);
    }

    @Test
    void sentBackPayrollReturnsToDraftWithTheReason() {
        Payroll payroll = new Payroll();
        payroll.process(hr, NOW);

        assertThatThrownBy(() -> payroll.sendBack(gm, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        payroll.sendBack(gm, " Overtime on Oct 3 looks wrong ");

        assertThat(payroll.getStatus()).isEqualTo(Payroll.Status.DRAFT);
        assertThat(payroll.getReviewNote()).isEqualTo("Overtime on Oct 3 looks wrong");
        assertThat(payroll.getProcessedBy()).isNull();
        assertThat(payroll.getProcessedAt()).isNull();

        payroll.process(hr, NOW.plusDays(1));

        assertThat(payroll.getReviewNote()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = Payroll.Status.class, names = {"DRAFT", "APPROVED", "PAID"})
    void onlyProcessedPayrollCanBeReviewed(Payroll.Status status) {
        Payroll payroll = new Payroll();
        ReflectionTestUtils.setField(payroll, "status", status);

        assertThatThrownBy(() -> payroll.approve(gm, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only PROCESSED payroll can be approved or sent back");
        assertThatThrownBy(() -> payroll.sendBack(gm, "reason"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(payroll.getStatus()).isEqualTo(status);
    }

    // ---------- overtime: PENDING -> APPROVED or REJECTED ----------

    @Test
    void pendingOvertimeCanBeApproved() {
        Overtime overtime = new Overtime();

        overtime.approve(hr);

        assertThat(overtime.getStatus()).isEqualTo(ApprovalRequest.Status.APPROVED);
        assertThat(overtime.getApprovedBy()).isSameAs(hr);
    }

    @Test
    void pendingOvertimeCanBeRejected() {
        Overtime overtime = new Overtime();

        overtime.reject(hr);

        assertThat(overtime.getStatus()).isEqualTo(ApprovalRequest.Status.REJECTED);
        assertThat(overtime.getApprovedBy()).isSameAs(hr);
    }

    // ---------- employee contact details ----------

    @Test
    void contactDetailsAreTrimmedAndBlanksStoredAsNull() {
        Employee employee = new Employee();

        employee.updateContactDetails("  ana@example.test ", " 0917 123 4567 ", "   ");

        assertThat(employee.getEmail()).isEqualTo("ana@example.test");
        assertThat(employee.getPhone()).isEqualTo("0917 123 4567");
        assertThat(employee.getAddress()).isNull();
    }

    @Test
    void contactDetailsNeedAnEmail() {
        Employee employee = new Employee();
        employee.setEmail("kept@example.test");

        assertThatThrownBy(() -> employee.updateContactDetails(" ", "0917", "Cebu"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email is required.");
        assertThat(employee.getEmail()).isEqualTo("kept@example.test");
        assertThat(employee.getPhone()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = ApprovalRequest.Status.class, names = {"APPROVED", "REJECTED"})
    void decidedOvertimeCannotBeDecidedAgain(ApprovalRequest.Status status) {
        Overtime overtime = new Overtime();
        ReflectionTestUtils.setField(overtime, "status", status);

        assertThatThrownBy(() -> overtime.approve(hr))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending overtime can be approved or rejected");
        assertThatThrownBy(() -> overtime.reject(hr))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending overtime can be approved or rejected");
        assertThat(overtime.getStatus()).isEqualTo(status);
        assertThat(overtime.getApprovedBy()).isNull();
    }
}
