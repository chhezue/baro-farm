package com.barofarm.buyer.inventory.presentation;

import com.barofarm.buyer.common.response.ResponseDto;
import com.barofarm.buyer.inventory.application.InventoryFacadeService;
import com.barofarm.buyer.inventory.application.InventoryService;
import com.barofarm.buyer.inventory.presentation.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/inventories")
@RequiredArgsConstructor
@Slf4j
public class InventoryInternalController {

    private final InventoryFacadeService inventoryFacadeService;
    private final InventoryService inventoryService;

    @PostMapping("/reserve")
    public ResponseDto<Void> reserveInventory(@Valid @RequestBody InventoryReserveRequest request) {
        log.info("createOrder called: {}", request);
        inventoryFacadeService.reserveInventory(request.toCommand());
        return ResponseDto.ok(null);
    }

    @PostMapping("/cancel")
    public ResponseDto<Void> cancelInventory(@Valid @RequestBody InventoryCancelRequest request) {
        inventoryFacadeService.cancelInventory(request.toCommand());
        return ResponseDto.ok(null);
    }
}
