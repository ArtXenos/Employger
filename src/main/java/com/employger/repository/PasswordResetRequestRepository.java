package com.employger.repository;

import com.employger.entity.PasswordResetRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Integer> {

    List<PasswordResetRequest> findByStatusOrderByRequestedAtAscRequestIdAsc(PasswordResetRequest.Status status);

    List<PasswordResetRequest> findByUserUserIdAndStatus(Integer userId, PasswordResetRequest.Status status);

    boolean existsByUserUserIdAndStatus(Integer userId, PasswordResetRequest.Status status);

    long countByStatus(PasswordResetRequest.Status status);
}
