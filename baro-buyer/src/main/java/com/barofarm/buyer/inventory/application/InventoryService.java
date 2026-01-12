package com.barofarm.buyer.inventory.application;

import com.barofarm.buyer.common.exception.CustomException;
import com.barofarm.buyer.inventory.application.dto.request.*;
import com.barofarm.buyer.inventory.domain.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.barofarm.buyer.inventory.exception.InventoryErrorCode.*;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository inventoryReservationRepository;

    @Transactional
    public void reserveInventory(InventoryReserveCommand command){

        try{
            // 멱등성 검증 로직 : 이미 커밋된 후 나중에 다시 호출된 경우
            List<InventoryReservation> reservations = inventoryReservationRepository.findAllByOrderId(command.orderId());
            boolean allReserved = !reservations.isEmpty() &&
                reservations.stream()
                    .allMatch(r -> r.getInventoryReservationStatus() == InventoryReservationStatus.RESERVED);
            if (allReserved) {
                return;
            }

            for (InventoryReserveCommand.Item item : command.items()) {
                Inventory inventory = inventoryRepository.findById(item.inventoryId())
                    .orElseThrow(() -> new CustomException(INVENTORY_NOT_FOUND));

                inventory.markReserve(item.quantity());
                inventory.addInventoryReservation(
                    InventoryReservation.of(command.orderId(), item.quantity())
                );
            }
        }
        // 멱등성 검증 로직 : 동시에 두번 들어가서 유니크 키 충돌이 발생하는 경우(낙관적 락 충돌보다 먼저 일어나는 케이스 보정)
        catch(DataIntegrityViolationException e){
            System.out.println("qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq");
            List<InventoryReservation> existing = inventoryReservationRepository.findAllByOrderId(command.orderId());
            boolean allReserved = !existing.isEmpty() &&
                existing.stream()
                    .allMatch(r -> r.getInventoryReservationStatus() == InventoryReservationStatus.RESERVED);
            if (allReserved) {
                return;
            }
            throw e;
        }
    }

    public void confirmInventory(InventoryConfirmCommand command) {
        List<InventoryReservation> reservations = inventoryReservationRepository.findAllByOrderId(command.orderId());

        if(reservations.isEmpty()){
            throw new CustomException(INVENTORY_RESERVATION_NOT_FOUND);
        }

        boolean allConfirmed = reservations.stream()
            .allMatch(r -> r.getInventoryReservationStatus() == InventoryReservationStatus.CONFIRMED);
        if (allConfirmed) {
            return;
        }

        boolean anyCanceled = reservations.stream()
            .anyMatch(r -> r.getInventoryReservationStatus() == InventoryReservationStatus.CANCELED);
        if (anyCanceled) {
            throw new CustomException(ALREADY_CANCELED);
        }

        for(InventoryReservation inventoryReservation : reservations){
            Inventory inventory = inventoryReservation.getInventory();
            Long requestedQuantity = inventoryReservation.getReservedQuantity();

            inventory.confirm(requestedQuantity);
            inventoryReservation.confirm();
        }
    }

    @Transactional
    public void cancelInventory(InventoryCancelCommand command) {

        List<InventoryReservation> reservations = inventoryReservationRepository.findAllByOrderId(command.orderId());

        if(reservations.isEmpty()){
            return;
        }

        boolean allCanceled = reservations.stream()
            .allMatch(r -> r.getInventoryReservationStatus() == InventoryReservationStatus.CANCELED);
        if (allCanceled) {
            return;
        }

        for (InventoryReservation inventoryReservation : reservations) {
            Inventory inventory = inventoryReservation.getInventory();
            Long requestedQuantity = inventoryReservation.getReservedQuantity();

            inventory.markCancel(requestedQuantity);
            inventoryReservation.markCancel();
        }
    }

    @Transactional
    public UUID createInventory(InventoryCreateCommand command) {
        Inventory inventory = Inventory.of(
            command.productId(),
            command.quantity(),
            command.unit()
        );
        Inventory saved = inventoryRepository.save(inventory);
        return saved.getId();
    }

    @Transactional
    public void deleteInventory(UUID inventoryId) {
        Inventory inventory = inventoryRepository.findById(inventoryId)
            .orElseThrow(() -> new CustomException(INVENTORY_NOT_FOUND));

        // 예약 수량이 남아 있으면 삭제 불가 (필요 없으면 이 if 블록 제거)
        if (inventory.getReservedQuantity() > 0) {
            throw new CustomException(INVENTORY_HAS_ACTIVE_RESERVATIONS);
        }

        inventoryRepository.delete(inventory);
    }
}
