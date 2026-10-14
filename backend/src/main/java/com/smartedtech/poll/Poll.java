package com.smartedtech.poll;

import com.smartedtech.session.LiveSession;
import com.smartedtech.user.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "polls")
public class Poll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private LiveSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "DRAFT"; // DRAFT, OPEN, CLOSED

    @Column(name = "results_visible_to_students", nullable = false)
    private boolean resultsVisibleToStudents = false;

    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<PollOption> options = new ArrayList<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public Poll() {
    }

    public Poll(LiveSession session, User creator, String question, boolean resultsVisibleToStudents) {
        this.session = session;
        this.creator = creator;
        this.question = question;
        this.resultsVisibleToStudents = resultsVisibleToStudents;
        this.status = "DRAFT";
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

    public User getCreator() {
        return creator;
    }

    public void setCreator(User creator) {
        this.creator = creator;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isResultsVisibleToStudents() {
        return resultsVisibleToStudents;
    }

    public void setResultsVisibleToStudents(boolean resultsVisibleToStudents) {
        this.resultsVisibleToStudents = resultsVisibleToStudents;
    }

    public List<PollOption> getOptions() {
        return options;
    }

    public void setOptions(List<PollOption> options) {
        this.options = options;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
