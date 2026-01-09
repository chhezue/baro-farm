package com.barofarm.buyer.inventory.presentation;

import com.barofarm.buyer.common.response.ResponseDto;
import com.barofarm.buyer.inventory.application.InventoryFacadeService;
import com.barofarm.buyer.inventory.presentation.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/inventories")
@RequiredArgsConstructor
public class InventoryInternalController {

    private final InventoryFacadeService inventoryFacadeService;

    @PostMapping("/reserve")
    public ResponseDto<Void> reserveInventory(@Valid @RequestBody InventoryReserveRequest request) {
        inventoryFacadeService.reserveInventory(request.toCommand());
        return ResponseDto.ok(null);
    }

//    // 카프카로 처리해야 함
//    @PostMapping("/confirm")
//    public ResponseDto<Void> confirmInventory(@Valid @RequestBody InventoryConfirmRequest request) {
//        inventoryFacadeService.confirmInventory(request.toCommand());
//        return ResponseDto.ok(null);
//    }

    @PostMapping("/cancel")
    public ResponseDto<Void> cancelInventory(@Valid @RequestBody InventoryCancelRequest request) {
        inventoryFacadeService.cancelInventory(request.toCommand());
        return ResponseDto.ok(null);
    }
}
