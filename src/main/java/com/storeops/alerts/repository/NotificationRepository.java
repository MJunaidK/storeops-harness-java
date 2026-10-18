package com.storeops.alerts.repository;

import com.storeops.alerts.model.Notification;
import java.util.List;

public interface NotificationRepository {

    Notification save(Notification notification);

    List<Notification> findByUserId(String userId);
}
