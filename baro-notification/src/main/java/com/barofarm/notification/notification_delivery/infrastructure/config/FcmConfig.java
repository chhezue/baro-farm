package com.barofarm.notification.notification_delivery.infrastructure.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * firebase Admin SDK 珥덇린??
 *
 * - NOTE : serviceAccount.json? ?덈? Git?????щ━寃?二쇱쓽
 * - /mnt/s3 ?먮뒗 Secret Manager 湲곕컲 寃쎈줈濡?二쇱엯 (?덉쟾??怨좊젮)
 * */

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

        // ?대? 珥덇린?붾맂 寃쎌슦 ?ъ큹湲고솕 諛⑹?
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
