package com.smartedtech.notification;

import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ClassroomEnrollmentRepository enrollmentRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            ClassroomEnrollmentRepository enrollmentRepository) {
        this.notificationRepository = notificationRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Transactional
    public void createNotification(User user, String type, String title, String body) {
        Notification notification = new Notification(user, type, title, body);
        notificationRepository.save(notification);
    }

    @Transactional
    public void notifyClassroomStudents(Classroom classroom, String type, String title, String body) {
        var enrollments = enrollmentRepository.findByClassroomId(classroom.getId());
        List<Notification> notifications = enrollments.stream()
                .map(e -> new Notification(e.getStudent(), type, title, body))
                .toList();
        notificationRepository.saveAll(notifications);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationDto::from);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFlagFalse(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        notificationRepository.markAsRead(notificationId, userId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }
}
