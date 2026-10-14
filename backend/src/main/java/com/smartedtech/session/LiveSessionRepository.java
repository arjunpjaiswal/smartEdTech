package com.smartedtech.session;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {

    @Query("SELECT s FROM LiveSession s JOIN FETCH s.classroom c JOIN FETCH c.teacher WHERE s.id = :id")
    Optional<LiveSession> findByIdWithClassroomAndTeacher(@Param("id") Long id);

    Page<LiveSession> findByClassroomId(Long classroomId, Pageable pageable);

    Page<LiveSession> findByClassroomIdOrderByScheduledStartTimeDesc(Long classroomId, Pageable pageable);

    long countByClassroomId(Long classroomId);

    @Query("SELECT s FROM LiveSession s WHERE s.classroom.teacher.id = :teacherId AND s.scheduledEndTime >= :now AND s.status != 'CANCELLED' ORDER BY s.scheduledStartTime ASC")
    List<LiveSession> findUpcomingForTeacher(@Param("teacherId") Long teacherId, @Param("now") Instant now, Pageable pageable);

    @Query("SELECT s FROM LiveSession s WHERE s.classroom.id IN (SELECT e.classroom.id FROM ClassroomEnrollment e WHERE e.student.id = :studentId) AND s.scheduledEndTime >= :now AND s.status != 'CANCELLED' ORDER BY s.scheduledStartTime ASC")
    List<LiveSession> findUpcomingForStudent(@Param("studentId") Long studentId, @Param("now") Instant now, Pageable pageable);

    @Query("SELECT s FROM LiveSession s WHERE s.scheduledEndTime >= :now AND s.status != 'CANCELLED' ORDER BY s.scheduledStartTime ASC")
    List<LiveSession> findUpcomingAll(@Param("now") Instant now, Pageable pageable);

    @Query("SELECT COUNT(s) FROM LiveSession s WHERE s.classroom.teacher.id = :teacherId AND s.scheduledEndTime >= :now AND s.status != 'CANCELLED'")
    long countUpcomingForTeacher(@Param("teacherId") Long teacherId, @Param("now") Instant now);

    @Query("SELECT COUNT(s) FROM LiveSession s WHERE s.classroom.id IN (SELECT e.classroom.id FROM ClassroomEnrollment e WHERE e.student.id = :studentId) AND s.scheduledEndTime >= :now AND s.status != 'CANCELLED'")
    long countUpcomingForStudent(@Param("studentId") Long studentId, @Param("now") Instant now);
}
