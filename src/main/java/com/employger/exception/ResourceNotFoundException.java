package com.employger.exception;

/**
 * A requested record (employee, payroll, pay cycle, ...) does not exist.
 * Controllers map it to HTTP 404; any other failure must not be reported
 * as "not found".
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
