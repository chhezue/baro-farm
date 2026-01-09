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

    // 카프카로 처리해야 함.
//    @Transactional
//    public void confirmInventory(InventoryConfirmCommand command) {
//        List<InventoryReservation> reservations = inventoryReservationRepository.findAllByOrderId(command.orderId());
//
//        if(reservations.isEmpty()){
//            throw new CustomException(INVENTORY_RESERVATION_NOT_FOUND);
//        }
//
//        boolean allConfirmed = reservations.stream()
//            .allMatch(r -> r.getInventoryReservationStatus() == InventoryReservationStatus.CONFIRMED);
//        if (allConfirmed) {
//            return;
//        }
//
//        boolean anyCanceled = reservations.stream()
//            .anyMatch(r -> r.getInventoryReservationStatus() == InventoryReservationStatus.CANCELED);
//        if (anyCanceled) {
//            throw new CustomException(ALREADY_CANCELED);
//        }
//
//        for(InventoryReservation inventoryReservation : reservations){
//            Inventory inventory = inventoryReservation.getInventory();
//            Long requestedQuantity = inventoryReservation.getReservedQuantity();
//
//            inventory.confirm(requestedQuantity);
//            inventoryReservation.confirm();
//        }
//    }

    @Transactional
    public void cancelInventory(InventoryCancelCommand command) {

        List<InventoryReservation> reservations = inventoryReservationRepository.findAllByOrderId(command.orderId());

        if(reservations.isEmpty()){
            throw new CustomException(INVENTORY_RESERVATION_NOT_FOUND);
        }

        boolean allCanceled = reservations.stream()
            .allMatch(r -> r.getInventoryReservationStatus() == InventoryReservationStatus.CANCELED);
        if (allCanceled) {
            return;
        }

        boolean anyConfirmed = reservations.stream()
            .anyMatch(r -> r.getInventoryReservationStatus() == InventoryReservationStatus.CONFIRMED);
        if (anyConfirmed) {
            throw new CustomException(ALREADY_CONFIRMED);
        }

        for (InventoryReservation inventoryReservation : reservations) {
            Inventory inventory = inventoryReservation.getInventory();
            Long requestedQuantity = inventoryReservation.getReservedQuantity();

            inventory.markCancel(requestedQuantity);
            inventoryReservation.markCancel();
        }
    }
}
