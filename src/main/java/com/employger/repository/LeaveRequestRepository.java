package com.employger.repository;

import com.employger.entity.ApprovalRequest;
import com.employger.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Integer> {

    List<LeaveRequest> findAllByOrderByStartDateDesc();

    List<LeaveRequest> findByStatusOrderByStartDateAsc(ApprovalRequest.Status status);

    List<LeaveRequest> findByEmployeeEmployeeIdOrderByStartDateDesc(Integer employeeId);

    // The employee's leave in these statuses that touches [start, end].
    List<LeaveRequest> findByEmployeeEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Integer employeeId, Collection<ApprovalRequest.Status> statuses, LocalDate end, LocalDate start);
}
