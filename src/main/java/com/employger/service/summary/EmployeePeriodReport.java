package com.employger.service.summary;

import com.employger.entity.Employee;
import com.employger.repository.EmployeeRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A month's summary with one row per employee, built from one kind of
 * record (attendance, overtime). Which employees get a row, and in what
 * order, is decided here once; each subclass only says which records to
 * read and how one employee's records become a row.
 *
 * @param <T> the record read, e.g. Attendance
 * @param <R> the row produced for one employee
 */
public abstract class EmployeePeriodReport<T, R> {

    private static final Comparator<Employee> BY_NAME = Comparator
            .comparing((Employee employee) -> employee.getLastName().toLowerCase())
            .thenComparing(employee -> employee.getFirstName().toLowerCase())
            .thenComparing(Employee::getEmployeeId);

    private final EmployeeRepository employeeRepository;

    protected EmployeePeriodReport(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /** The records dated within the month. */
    protected abstract List<T> recordsBetween(LocalDate from, LocalDate to);

    /** The employee a record belongs to. */
    protected abstract Employee employeeOf(T record);

    /** One employee's row; records is empty when they have none that month. */
    protected abstract R rowFor(Employee employee, List<T> records);

    /**
     * One row per employee, by last name: every active employee employed
     * during the month, and anyone else with records in it. Final, so both
     * summaries list exactly the same people.
     */
    protected final List<R> rows(YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        Map<Integer, Employee> employees = new LinkedHashMap<>();
        Map<Integer, List<T>> recordsByEmployee = new HashMap<>();

        for (T record : recordsBetween(from, to)) {
            Employee employee = employeeOf(record);
            employees.putIfAbsent(employee.getEmployeeId(), employee);
            recordsByEmployee.computeIfAbsent(employee.getEmployeeId(), id -> new ArrayList<>()).add(record);
        }

        for (Employee employee : employeeRepository.findAll()) {
            if (employedDuring(employee, from, to)) {
                employees.putIfAbsent(employee.getEmployeeId(), employee);
            }
        }

        return employees.values().stream()
                .sorted(BY_NAME)
                .map(employee -> rowFor(employee,
                        recordsByEmployee.getOrDefault(employee.getEmployeeId(), List.of())))
                .toList();
    }

    protected static String nameOf(Employee employee) {
        return employee.getLastName() + ", " + employee.getFirstName();
    }

    /** "November 2026". */
    protected static String periodOf(YearMonth month) {
        return month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                + " " + month.getYear();
    }

    private static boolean employedDuring(Employee employee, LocalDate from, LocalDate to) {
        return employee.getEmploymentStatus() == Employee.EmploymentStatus.ACTIVE
                && (employee.getDateHired() == null || !employee.getDateHired().isAfter(to))
                && (employee.getSeparationDate() == null || !employee.getSeparationDate().isBefore(from));
    }
}
