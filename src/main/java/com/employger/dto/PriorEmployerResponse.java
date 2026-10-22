package com.employger.dto;

import com.employger.entity.PriorEmployerCompensation;

import java.math.BigDecimal;

public record PriorEmployerResponse(
        Integer priorId,
        EmployeeSummary employee,
        Integer taxYear,
        String employerName,
        String employerTin,
        BigDecimal taxableCompensation,
        BigDecimal exemptBenefits,
        BigDecimal taxWithheld) {

    public static PriorEmployerResponse from(PriorEmployerCompensation record) {
        return new PriorEmployerResponse(
                record.getPriorId(),
                EmployeeSummary.from(record.getEmployee()),
                record.getTaxYear(),
                record.getEmployerName(),
                record.getEmployerTin(),
                record.getTaxableCompensation(),
                record.getExemptBenefits(),
                record.getTaxWithheld());
    }
}
