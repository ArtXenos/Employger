package com.employger.controller;

import com.employger.dto.LeaveResponse;
import com.employger.dto.ReasonRequest;
import com.employger.entity.ApprovalRequest;
import com.employger.service.LeaveService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Staff side of leave: list requests and approve or reject them. Employees
// file and cancel their own under /api/me/leave. Errors become plain text
// in ApiExceptionHandler.
@RestController
@RequestMapping("/api/leave")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    // ?status=PENDING returns only that status, oldest first.
    @GetMapping
    public List<LeaveResponse> getLeave(@RequestParam(required = false) ApprovalRequest.Status status) {
        return leaveService.getLeave(status).stream().map(LeaveResponse::new).toList();
    }

    // Rejecting needs {"reason": "..."} in the body; approving needs no body.
    @PutMapping("/{id}/status")
    public LeaveResponse decide(
            @PathVariable Integer id,
            @RequestParam ApprovalRequest.Status status,
            @RequestBody(required = false) ReasonRequest reason,
            Authentication authentication) {

        return new LeaveResponse(leaveService.decide(id, status, authentication.getName(), ReasonRequest.of(reason)));
    }
}
