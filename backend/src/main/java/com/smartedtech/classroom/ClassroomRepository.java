package com.smartedtech.classroom;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, Long> {

    Optional<Classroom> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);

    Page<Classroom> findByTeacherId(Long teacherId, Pageable pageable);

    List<Classroom> findTop5ByTeacherIdOrderByCreatedAtDesc(Long teacherId);

    long countByTeacherId(Long teacherId);

    long countByArchivedFalse();

    @Query("SELECT e.classroom FROM ClassroomEnrollment e WHERE e.student.id = :studentId")
    Page<Classroom> findEnrolledByStudentId(@Param("studentId") Long studentId, Pageable pageable);

    @Query("SELECT e.classroom FROM ClassroomEnrollment e WHERE e.student.id = :studentId ORDER BY e.createdAt DESC")
    List<Classroom> findTop5EnrolledByStudentId(@Param("studentId") Long studentId);
}
