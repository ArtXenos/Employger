package com.employger.service.kiosk;

import com.employger.entity.Employee;
import com.employger.exception.ResourceNotFoundException;
import com.employger.repository.EmployeeRepository;
import com.employger.service.AuditService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Attendance badges: HR issues one per employee and prints it. Reissuing
 * gives a new code, so a lost card stops working at once.
 */
@Service
public class BadgeService {

    // Two random codes colliding is practically impossible (80 bits), but
    // the database would refuse it anyway, so try again rather than fail.
    private static final int ATTEMPTS = 5;

    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    public BadgeService(EmployeeRepository employeeRepository, AuditService auditService) {
        this.employeeRepository = employeeRepository;
        this.auditService = auditService;
    }

    public Employee getEmployee(Integer employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    @Transactional
    public Employee issueBadge(Integer employeeId) {

        Employee employee = getEmployee(employeeId);
        boolean replacing = employee.getBadgeCode() != null;

        employee.issueBadge(unusedCode().value());
        Employee saved = employeeRepository.save(employee);

        auditService.record(replacing ? "BADGE_REISSUED" : "BADGE_ISSUED",
                (replacing ? "Reissued the attendance badge of employee #" : "Issued an attendance badge to employee #")
                        + employeeId + (replacing ? "; the previous card no longer works" : ""));

        return saved;
    }

    /** The employee's badge QR code as an SVG image. */
    public String qrSvg(Integer employeeId) {

        String code = getEmployee(employeeId).getBadgeCode();

        if (code == null) {
            throw new ResourceNotFoundException("This employee has no badge yet.");
        }

        return svg(code);
    }

    private BadgeCode unusedCode() {

        for (int i = 0; i < ATTEMPTS; i++) {
            BadgeCode code = BadgeCode.generate();

            if (!employeeRepository.existsByBadgeCode(code.value())) {
                return code;
            }
        }

        throw new IllegalStateException("Could not create a badge code. Please try again.");
    }

    // One square per dark module, on a white background with the standard
    // quiet zone around it. Medium error correction survives a scratched card.
    static String svg(String text) {

        BitMatrix matrix;

        try {
            matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 4));
        } catch (WriterException e) {
            throw new IllegalStateException("Could not draw the badge QR code.", e);
        }

        int width = matrix.getWidth();
        int height = matrix.getHeight();
        StringBuilder path = new StringBuilder();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
        }

        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + width + " " + height
                + "\" shape-rendering=\"crispEdges\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
                + "<path fill=\"#000\" d=\"" + path + "\"/></svg>";
    }
}
