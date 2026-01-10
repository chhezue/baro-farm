package com.barofarm.buyer.inventory.application;

import com.barofarm.buyer.common.exception.CustomException;
import com.barofarm.buyer.inventory.application.dto.request.InventoryCancelCommand;
import com.barofarm.buyer.inventory.application.dto.request.InventoryConfirmCommand;
import com.barofarm.buyer.inventory.application.dto.request.InventoryReserveCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import static com.barofarm.buyer.inventory.exception.InventoryErrorCode.RESERVATION_RETRY_EXCEEDED;

@Component
@RequiredArgsConstructor
public class InventoryFacadeService {

    private static final int MAX_RETRY = 3;

    private final InventoryService inventoryService;

    public void reserveInventory(InventoryReserveCommand command) {
        retry(() -> inventoryService.reserveInventory(command));
    }

    public void confirmInventory(InventoryConfirmCommand command) {
        retry(() -> inventoryService.confirmInventory(command));
    }

    public void cancelInventory(InventoryCancelCommand command) {
        retry(() -> inventoryService.cancelInventory(command));
    }

    private void retry(Runnable action) {
        int tryCount = 0;

        while (tryCount < MAX_RETRY) {
            try {
                action.run();
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                tryCount++;
            }
        }

        throw new CustomException(RESERVATION_RETRY_EXCEEDED);
    }
}
