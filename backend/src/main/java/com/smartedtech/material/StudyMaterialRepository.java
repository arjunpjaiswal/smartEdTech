package com.smartedtech.material;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudyMaterialRepository extends JpaRepository<StudyMaterial, Long> {

    Page<StudyMaterial> findByClassroomIdOrderByCreatedAtDesc(Long classroomId, Pageable pageable);

    long countByClassroomId(Long classroomId);

    @Query("SELECT COUNT(m) FROM StudyMaterial m WHERE m.classroom.teacher.id = :teacherId")
    long countByTeacherId(@Param("teacherId") Long teacherId);

    @Query("SELECT COUNT(m) FROM StudyMaterial m WHERE m.classroom.id IN (SELECT e.classroom.id FROM ClassroomEnrollment e WHERE e.student.id = :studentId)")
    long countByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT m FROM StudyMaterial m WHERE m.classroom.id IN (SELECT e.classroom.id FROM ClassroomEnrollment e WHERE e.student.id = :studentId) ORDER BY m.createdAt DESC")
    List<StudyMaterial> findTop5RecentForStudent(@Param("studentId") Long studentId, Pageable pageable);
}
