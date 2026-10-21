package com.employger.service;

import com.employger.entity.PayCycle;
import com.employger.repository.PayCycleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class PayCycleService {

        private final PayCycleRepository payCycleRepository;

        public PayCycleService(PayCycleRepository payCycleRepository) {
                this.payCycleRepository = payCycleRepository;
        }

        public List<PayCycle> getAllPayCycles() {
                return payCycleRepository.findAll();
        }

        public Optional<PayCycle> getPayCycleById(Integer id) {
                return payCycleRepository.findById(id);
        }

        public PayCycle createPayCycle(PayCycle payCycle) {

                validatePayCycle(payCycle);

                // Never trust a client-supplied ID: save() would otherwise
                // overwrite the existing pay cycle with that ID.
                payCycle.setPayCycleId(null);

                return payCycleRepository.save(payCycle);
        }

        public PayCycle updatePayCycle(
                        Integer id,
                        PayCycle payCycleDetails) {

                validatePayCycle(payCycleDetails);

                PayCycle payCycle = payCycleRepository
                                .findById(id)
                                .orElseThrow(() -> new RuntimeException(
                                                "Pay cycle not found"));

                payCycle.setCycleName(
                                payCycleDetails.getCycleName());

                payCycle.setStartDate(
                                payCycleDetails.getStartDate());

                payCycle.setEndDate(
                                payCycleDetails.getEndDate());

                payCycle.setPayDate(
                                payCycleDetails.getPayDate());

                payCycle.setStatus(
                                payCycleDetails.getStatus());

                payCycle.setCycleType(
                                payCycleDetails.getCycleType());

                return payCycleRepository.save(payCycle);
        }

        public void deletePayCycle(Integer id) {

                if (!payCycleRepository.existsById(id)) {
                        throw new RuntimeException(
                                        "Pay cycle not found");
                }

                payCycleRepository.deleteById(id);
        }

        private void validatePayCycle(PayCycle payCycle) {

                if (payCycle.getCycleName() == null
                                || payCycle.getCycleName().isBlank()) {

                        throw new IllegalArgumentException(
                                        "Cycle name is required");
                }

                if (payCycle.getStartDate() == null
                                || payCycle.getEndDate() == null
                                || payCycle.getPayDate() == null) {

                        throw new IllegalArgumentException(
                                        "Start date, end date, and pay date are required");
                }

                LocalDate startDate = payCycle.getStartDate();
                LocalDate endDate = payCycle.getEndDate();
                LocalDate payDate = payCycle.getPayDate();

                if (endDate.isBefore(startDate)) {

                        throw new IllegalArgumentException(
                                        "End date cannot be before start date");
                }

                if (payDate.isBefore(endDate)) {

                        throw new IllegalArgumentException(
                                        "Pay date cannot be before end date");
                }

                if (payCycle.getCycleType() == null) {

                        throw new IllegalArgumentException(
                                        "Cycle type is required");
                }

                long numberOfDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

                switch (payCycle.getCycleType()) {

                        case WEEKLY:

                                if (numberOfDays != 7) {
                                        throw new IllegalArgumentException(
                                                        "Weekly pay cycles must cover exactly 7 days");
                                }

                                break;

                        case BI_WEEKLY:

                                if (numberOfDays != 14) {
                                        throw new IllegalArgumentException(
                                                        "Bi-weekly pay cycles must cover exactly 14 days");
                                }

                                break;

                        case SEMI_MONTHLY:

                                boolean firstHalf = startDate.getDayOfMonth() == 1
                                                && endDate.getDayOfMonth() == 15;

                                boolean secondHalf = startDate.getDayOfMonth() == 16
                                                && endDate.getDayOfMonth() == endDate.lengthOfMonth();

                                if (!firstHalf && !secondHalf) {
                                        throw new IllegalArgumentException(
                                                        "Semi-monthly cycles must cover either days 1-15 or 16-last day of the month");
                                }

                                break;

                        case MONTHLY:

                                boolean startsFirstDay = startDate.getDayOfMonth() == 1;

                                boolean endsLastDay = endDate.getDayOfMonth() == endDate.lengthOfMonth();

                                if (!startsFirstDay || !endsLastDay) {
                                        throw new IllegalArgumentException(
                                                        "Monthly cycles must cover the entire month");
                                }

                                break;

                        default:

                                throw new IllegalArgumentException(
                                                "Invalid cycle type");
                }
        }
}