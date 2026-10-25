package com.employger.service;

import com.employger.entity.Employee;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A PAID payroll as a one-page PDF payslip. Built on request from the stored
 * payroll and never saved, so no copy of anyone's pay sits in a file.
 *
 * Amounts are written "PHP 1,234.56": the PDF's built-in fonts have no peso
 * sign. Lines that don't apply (zero or not recorded) are left out.
 */
public final class PayslipPdf {

    private static final Font TITLE = new Font(Font.HELVETICA, 15, Font.BOLD);
    private static final Font HEADING = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font LABEL = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(90, 90, 90));
    private static final Font TEXT = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font BOLD = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font NET = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font SMALL = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, new Color(110, 110, 110));
    private static final Color RULE = new Color(200, 200, 200);
    private static final Color SHADE = new Color(240, 243, 247);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("MMMM d, yyyy h:mm a", Locale.ENGLISH);

    private final Payroll payroll;
    private final String companyName;
    private final LocalDateTime generatedAt;

    public PayslipPdf(Payroll payroll, String companyName, LocalDateTime generatedAt) {
        if (payroll.getStatus() != Payroll.Status.PAID) {
            throw new IllegalStateException("A payslip PDF is only available for PAID payroll.");
        }
        this.payroll = payroll;
        this.companyName = companyName == null || companyName.isBlank() ? "Em-Ployger" : companyName.trim();
        this.generatedAt = generatedAt;
    }

    /** e.g. payslip-2026-10-01-employee-35.pdf */
    public String fileName() {
        return "payslip-" + payroll.getPayCycle().getStartDate()
                + "-employee-" + payroll.getEmployee().getEmployeeId() + ".pdf";
    }

    public byte[] toBytes() {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 42, 42, 40, 40);

        try {
            PdfWriter.getInstance(document, out);
            document.addTitle("Payslip " + payroll.getPayCycle().getCycleName());
            document.addCreator("Em-Ployger");
            document.open();

            writeHeader(document);
            writeDetails(document);
            writeAmounts(document);
            writeNetPay(document);
            writeFooter(document);
        } catch (DocumentException e) {
            throw new IllegalStateException("The payslip PDF could not be created.", e);
        } finally {
            document.close();
        }

        return out.toByteArray();
    }

    // ---------- sections ----------

    private void writeHeader(Document document) throws DocumentException {
        Paragraph company = new Paragraph(companyName, TITLE);
        document.add(company);

        Paragraph title = new Paragraph("PAYSLIP  ·  " + payroll.getPayCycle().getCycleName(), HEADING);
        title.setSpacingAfter(12);
        document.add(title);
    }

    private void writeDetails(Document document) throws DocumentException {
        Employee employee = payroll.getEmployee();
        PayCycle cycle = payroll.getPayCycle();

        PdfPTable table = new PdfPTable(new float[] {1.1f, 1.9f, 1.1f, 1.9f});
        table.setWidthPercentage(100);

        detail(table, "Employee", employee.getFirstName() + " " + employee.getLastName());
        detail(table, "Employee no.", String.valueOf(employee.getEmployeeId()));
        detail(table, "Department", orDash(payroll.getDepartment() != null && !payroll.getDepartment().isBlank()
                ? payroll.getDepartment() : employee.getDepartment()));
        detail(table, "Job title", orDash(employee.getJobTitle()));
        detail(table, "Pay period", date(cycle.getStartDate()) + " to " + date(cycle.getEndDate()));
        detail(table, "Pay date", date(cycle.getPayDate()));
        detail(table, "Paid on", payroll.getPaymentDate() != null
                ? DATE.format(payroll.getPaymentDate()) : "-");
        detail(table, "Payment", orDash(payroll.getPaymentMethod())
                + (payroll.getPaymentReference() != null && !payroll.getPaymentReference().isBlank()
                        ? " (ref. " + payroll.getPaymentReference() + ")" : ""));

        table.setSpacingAfter(14);
        document.add(table);
    }

    private void writeAmounts(Document document) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[] {1f, 1f});
        table.setWidthPercentage(100);

        table.addCell(column(earnings()));
        table.addCell(column(deductions()));

        table.setSpacingAfter(12);
        document.add(table);
    }

    private void writeNetPay(Document document) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[] {1f, 1f});
        table.setWidthPercentage(100);

        PdfPCell label = new PdfPCell(new Phrase("NET PAY", NET));
        PdfPCell amount = new PdfPCell(new Phrase(money(payroll.getNetPay()), NET));
        amount.setHorizontalAlignment(Element.ALIGN_RIGHT);

        // One shaded bar: no line between the two cells.
        label.setBorder(PdfPCell.LEFT | PdfPCell.TOP | PdfPCell.BOTTOM);
        amount.setBorder(PdfPCell.RIGHT | PdfPCell.TOP | PdfPCell.BOTTOM);

        for (PdfPCell cell : List.of(label, amount)) {
            cell.setBackgroundColor(SHADE);
            cell.setBorderColor(RULE);
            cell.setPadding(8);
            table.addCell(cell);
        }

        table.setSpacingAfter(18);
        document.add(table);
    }

    private void writeFooter(Document document) throws DocumentException {
        document.add(new Paragraph(
                "Payroll #" + payroll.getPayrollId() + ". System-generated payslip from Em-Ployger, "
                        + DATE_TIME.format(generatedAt) + ". Keep it for your records.", SMALL));
    }

    // ---------- lines ----------

    /** One line of the payslip: a label, an amount and whether it's a total. */
    private record Line(String label, BigDecimal amount, boolean total) {
    }

    private List<Line> earnings() {
        List<Line> lines = new ArrayList<>();

        lines.add(new Line("EARNINGS", null, false));
        optional(lines, "Base pay", payroll.getBasePay());
        optional(lines, "Less: absences and undertime", negate(payroll.getAttendanceDeduction()));
        lines.add(new Line("Regular pay", payroll.getRegularPay(), false));
        optional(lines, "Overtime pay", payroll.getOvertimePay());
        optional(lines, "Holiday and rest-day premium", payroll.getHolidayPremiumPay());
        optional(lines, "Night differential", payroll.getNightDifferentialPay());

        BigDecimal itemized = BigDecimal.ZERO;
        itemized = optional(lines, "13th month pay", payroll.getThirteenthMonthPay(), itemized);
        itemized = optional(lines, "Other benefits", payroll.getOtherBenefitsPay(), itemized);
        itemized = optional(lines, "Hazard pay", payroll.getHazardPay(), itemized);
        itemized = optional(lines, "De minimis benefits", payroll.getDeMinimisPay(), itemized);
        itemized = optional(lines, "Taxable allowances", payroll.getTaxableAllowancePay(), itemized);

        // Payroll from before the itemized columns (V6) has only the total.
        BigDecimal rest = nz(payroll.getBonusAmount()).subtract(itemized);
        optional(lines, "Bonuses and benefits", rest);
        optional(lines, "Corrections of earlier pay", payroll.getAdjustmentAmount());

        lines.add(new Line("Gross pay", payroll.getGrossPay(), true));
        return lines;
    }

    private List<Line> deductions() {
        List<Line> lines = new ArrayList<>();

        lines.add(new Line("DEDUCTIONS", null, false));
        optional(lines, "SSS", payroll.getSssContribution());
        optional(lines, "PhilHealth", payroll.getPhilHealthContribution());
        optional(lines, "Pag-IBIG", payroll.getPagIbigContribution());
        lines.add(new Line("Withholding tax", nz(payroll.getTaxAmount()), false));
        lines.add(new Line("Total deductions",
                nz(payroll.getDeductionAmount()).add(nz(payroll.getTaxAmount())), true));
        optional(lines, "Add: year-end tax refund", payroll.getTaxRefund());
        return lines;
    }

    private static void optional(List<Line> lines, String label, BigDecimal amount) {
        if (amount != null && amount.signum() != 0) {
            lines.add(new Line(label, amount, false));
        }
    }

    private static BigDecimal optional(List<Line> lines, String label, BigDecimal amount, BigDecimal runningTotal) {
        optional(lines, label, amount);
        return runningTotal.add(nz(amount));
    }

    // ---------- cells ----------

    private static PdfPCell column(List<Line> lines) {
        PdfPTable inner = new PdfPTable(new float[] {1.6f, 1f});
        inner.setWidthPercentage(100);

        for (Line line : lines) {
            if (line.amount() == null) {
                PdfPCell heading = new PdfPCell(new Phrase(line.label(), HEADING));
                heading.setColspan(2);
                heading.setBorder(PdfPCell.BOTTOM);
                heading.setBorderColor(RULE);
                heading.setPaddingBottom(5);
                inner.addCell(heading);
                continue;
            }

            Font font = line.total() ? BOLD : TEXT;
            PdfPCell label = new PdfPCell(new Phrase(line.label(), font));
            PdfPCell amount = new PdfPCell(new Phrase(money(line.amount()), font));
            amount.setHorizontalAlignment(Element.ALIGN_RIGHT);

            for (PdfPCell cell : List.of(label, amount)) {
                cell.setBorder(line.total() ? PdfPCell.TOP : PdfPCell.NO_BORDER);
                cell.setBorderColor(RULE);
                cell.setPaddingTop(line.total() ? 5 : 3);
                cell.setPaddingBottom(3);
                inner.addCell(cell);
            }
        }

        PdfPCell cell = new PdfPCell(inner);
        cell.setBorderColor(RULE);
        cell.setPadding(8);
        return cell;
    }

    private static void detail(PdfPTable table, String label, String value) {
        PdfPCell name = new PdfPCell(new Phrase(label, LABEL));
        PdfPCell text = new PdfPCell(new Phrase(value, TEXT));

        for (PdfPCell cell : List.of(name, text)) {
            cell.setBorder(PdfPCell.NO_BORDER);
            cell.setPaddingBottom(4);
            table.addCell(cell);
        }
    }

    // ---------- formatting ----------

    private static String money(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        BigDecimal value = nz(amount);
        return value.signum() < 0
                ? "-PHP " + format.format(value.negate())
                : "PHP " + format.format(value);
    }

    private static String date(LocalDate date) {
        return date == null ? "-" : DATE.format(date);
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static BigDecimal nz(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private static BigDecimal negate(BigDecimal amount) {
        return amount == null ? null : amount.negate();
    }
}
