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
public interface ClassroomEnrollmentRepository extends JpaRepository<ClassroomEnrollment, Long> {

    Optional<ClassroomEnrollment> findByClassroomIdAndStudentId(Long classroomId, Long studentId);

    boolean existsByClassroomIdAndStudentId(Long classroomId, Long studentId);

    Page<ClassroomEnrollment> findByClassroomId(Long classroomId, Pageable pageable);

    List<ClassroomEnrollment> findByClassroomId(Long classroomId);

    long countByClassroomId(Long classroomId);

    long countByStudentId(Long studentId);

    @Query("SELECT COUNT(e) FROM ClassroomEnrollment e WHERE e.classroom.teacher.id = :teacherId")
    long countStudentsByTeacherId(@Param("teacherId") Long teacherId);

    void deleteByClassroomIdAndStudentId(Long classroomId, Long studentId);
}
