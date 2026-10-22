package com.employger.dto;

import com.employger.entity.Overtime;

import java.math.BigDecimal;
import java.time.LocalDate;

public class OvertimeResponse {

    private Integer overtimeId;
    private Integer employeeId;
    private String employeeName;
    private Integer attendanceId;
    private LocalDate overtimeDate;
    private BigDecimal hours;
    private BigDecimal overtimeRate;
    private BigDecimal overtimeAmount;
    private String status;
    private String approvedBy;
    private Integer payrollId;

    public OvertimeResponse(Overtime overtime) {

        this.overtimeId = overtime.getOvertimeId();

        this.employeeId = overtime.getEmployee() != null
                ? overtime.getEmployee().getEmployeeId()
                : null;

        this.employeeName = overtime.getEmployee() != null
                ? overtime.getEmployee().getFirstName()
                    + " "
                    + overtime.getEmployee().getLastName()
                : null;

        this.attendanceId = overtime.getAttendance() != null
                ? overtime.getAttendance().getAttendanceId()
                : null;

        this.overtimeDate = overtime.getOvertimeDate();
        this.hours = overtime.getHours();
        this.overtimeRate = overtime.getOvertimeRate();
        this.overtimeAmount = overtime.getOvertimeAmount();

        this.status = overtime.getStatus() != null
                ? overtime.getStatus().name()
                : null;

        this.approvedBy = overtime.getApprovedBy() != null
                ? overtime.getApprovedBy().getUsername()
                : null;
        this.payrollId = overtime.getPayroll() != null
                ? overtime.getPayroll().getPayrollId()
                : null;
    }

    public Integer getOvertimeId() {
        return overtimeId;
    }

    public Integer getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public Integer getAttendanceId() {
        return attendanceId;
    }

    public LocalDate getOvertimeDate() {
        return overtimeDate;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public BigDecimal getOvertimeRate() {
        return overtimeRate;
    }

    public BigDecimal getOvertimeAmount() {
        return overtimeAmount;
    }

    public String getStatus() {
        return status;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public Integer getPayrollId() {
        return payrollId;
    }
}