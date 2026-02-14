package com.barofarm.notification.notification_delivery.application.port;

import com.barofarm.notification.notification_delivery.domain.model.RecipientProfile;

public interface RecipientResolverPort {
    RecipientProfile resolve(String userId);
}
