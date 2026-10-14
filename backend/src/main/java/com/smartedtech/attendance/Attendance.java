package com.smartedtech.attendance;

import com.smartedtech.session.LiveSession;
import com.smartedtech.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "attendance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_attendance_session_user", columnNames = {"session_id", "user_id"})
})
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private LiveSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "first_joined_at", nullable = false)
    private Instant firstJoinedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "duration_seconds", nullable = false)
    private long durationSeconds = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public Attendance() {
    }

    public Attendance(LiveSession session, User user, Instant joinedAt) {
        this.session = session;
        this.user = user;
        this.firstJoinedAt = joinedAt;
        this.lastSeenAt = joinedAt;
        this.durationSeconds = 0;
    }

    public Long getId() {
        return id;
    }

    public LiveSession getSession() {
        return session;
    }

    public void setSession(LiveSession session) {
        this.session = session;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Instant getFirstJoinedAt() {
        return firstJoinedAt;
    }

    public void setFirstJoinedAt(Instant firstJoinedAt) {
        this.firstJoinedAt = firstJoinedAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
