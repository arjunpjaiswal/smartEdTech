package com.smartedtech.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findBySessionIdAndUserId(Long sessionId, Long userId);

    List<Attendance> findBySessionIdOrderByFirstJoinedAtAsc(Long sessionId);

    long countBySessionId(Long sessionId);
}
