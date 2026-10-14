package com.smartedtech.notification;

import java.time.Instant;

public record NotificationDto(
        Long id,
        String type,
        String title,
        String body,
        boolean readFlag,
        Instant createdAt
) {
    public static NotificationDto from(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.isReadFlag(),
                notification.getCreatedAt()
        );
    }
}
