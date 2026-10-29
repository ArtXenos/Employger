package com.employger.controller;

import com.employger.dto.KioskScanRequest;
import com.employger.dto.KioskScanResponse;
import com.employger.service.kiosk.KioskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Badge scans from the attendance kiosk. Only the KIOSK login may call this
 * (SecurityConfig), and only from the kiosk computer itself: by default the
 * server's own machine (http://localhost:8080/kiosk.html). A kiosk login
 * used from any other device is refused, so clocking in always means
 * standing at the entrance.
 *
 * To use a separate kiosk computer instead, list its IP address in
 * KIOSK_ADDRESSES (application.properties).
 */
@RestController
@RequestMapping("/api/kiosk")
public class KioskController {

    static final String WRONG_DEVICE = "Scans are only accepted at the kiosk computer.";

    private final KioskService kioskService;
    private final List<InetAddress> extraKioskAddresses;

    public KioskController(
            KioskService kioskService,
            @Value("${employger.kiosk.allowed-addresses:}") List<String> extraKioskAddresses) {

        this.kioskService = kioskService;
        this.extraKioskAddresses = extraKioskAddresses.stream()
                .map(String::trim)
                .filter(address -> !address.isEmpty())
                .map(KioskController::ipAddress)
                .toList();
    }

    @PostMapping("/scan")
    public ResponseEntity<?> scan(
            @Valid @RequestBody KioskScanRequest request,
            HttpServletRequest servletRequest) {

        if (!fromKioskComputer(servletRequest.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                    .body(WRONG_DEVICE);
        }

        KioskScanResponse response = kioskService.scan(request.badgeCode());
        return ResponseEntity.ok(response);
    }

    // getRemoteAddr() is the computer that connected, not a header the
    // browser could set (no proxy is configured).
    private boolean fromKioskComputer(String remoteAddress) {
        try {
            InetAddress address = InetAddress.getByName(remoteAddress);
            return address.isLoopbackAddress() || extraKioskAddresses.contains(address);
        } catch (UnknownHostException e) {
            return false;
        }
    }

    // IP addresses only, so a startup never waits on a name lookup.
    private static InetAddress ipAddress(String text) {
        if (!text.matches("[0-9a-fA-F:.]+")) {
            throw new IllegalArgumentException(
                    "KIOSK_ADDRESSES must list IP addresses, e.g. 192.168.1.40; got: " + text);
        }
        try {
            return InetAddress.getByName(text);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("KIOSK_ADDRESSES has an invalid IP address: " + text, e);
        }
    }
}
