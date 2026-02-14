package com.barofarm.notification.notification_delivery.infrastructure.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Firebase Admin SDK 초기화 설정.
 * serviceAccount 키를 읽어 앱 초기화를 1회만 수행한다.
 */

@Configuration
public class FcmConfig {

    @Value("${notification.delivery.fcm.enabled:false}")
    private boolean enabled;

    @Value("${notification.delivery.fcm.credential-path:/mnt/s3/firebase/serviceAccount.json}")
    private String credentialPath;

    @PostConstruct
    public void init() throws Exception {
        if (!enabled) {
            return;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try (FileInputStream serviceAccount = new FileInputStream(credentialPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

            FirebaseApp.initializeApp(options);
        }
    }
}

