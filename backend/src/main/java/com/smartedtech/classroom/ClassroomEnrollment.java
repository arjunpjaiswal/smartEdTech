package com.smartedtech.classroom;

import com.smartedtech.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "classroom_enrollments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_enrollments_classroom_student", columnNames = {"classroom_id", "student_id"})
})
public class ClassroomEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    private Classroom classroom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public ClassroomEnrollment() {
    }

    public ClassroomEnrollment(Classroom classroom, User student) {
        this.classroom = classroom;
        this.student = student;
    }

    public Long getId() {
        return id;
    }

    public Classroom getClassroom() {
        return classroom;
    }

    public void setClassroom(Classroom classroom) {
        this.classroom = classroom;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
