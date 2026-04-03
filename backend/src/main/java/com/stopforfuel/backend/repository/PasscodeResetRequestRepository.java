package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.PasscodeResetRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PasscodeResetRequestRepository extends JpaRepository<PasscodeResetRequest, Long> {

    List<PasscodeResetRequest> findByScidAndStatusOrderByRequestedAtDesc(Long scid, PasscodeResetRequest.Status status);

    List<PasscodeResetRequest> findByScidOrderByRequestedAtDesc(Long scid);

    long countByScidAndStatus(Long scid, PasscodeResetRequest.Status status);
}
