package com.barofarm.support.notification_delivery.application.port;

import com.barofarm.support.notification_delivery.domain.model.RecipientProfile;

public interface RecipientResolverPort {
    RecipientProfile resolve(String userId);
}
