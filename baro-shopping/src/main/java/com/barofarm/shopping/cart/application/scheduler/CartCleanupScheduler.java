package com.barofarm.shopping.cart.application.scheduler;

import com.barofarm.shopping.cart.application.CartCleanupService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CartCleanupScheduler {

    private final CartCleanupService cartCleanupService;

    @Scheduled(cron = "0 0 4 * * *") // 매일 새벽 4시
    public void cleanupGuestCarts() {
        int deleted = cartCleanupService.cleanupExpiredGuestCarts(LocalDateTime.now());
        log.info("🧹 Guest Cart Cleanup Completed - Count of Deleted Cart: {}", deleted);
    }
}
