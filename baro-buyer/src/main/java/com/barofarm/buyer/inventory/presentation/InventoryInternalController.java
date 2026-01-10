package com.barofarm.buyer.inventory.presentation;

import com.barofarm.buyer.common.response.ResponseDto;
import com.barofarm.buyer.inventory.application.InventoryFacadeService;
import com.barofarm.buyer.inventory.application.InventoryService;
import com.barofarm.buyer.inventory.application.dto.request.InventoryCreateCommand;
import com.barofarm.buyer.inventory.presentation.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/inventories")
@RequiredArgsConstructor
public class InventoryInternalController {

    private final InventoryFacadeService inventoryFacadeService;
    private final InventoryService inventoryService;

    @PostMapping("/reserve")
    public ResponseDto<Void> reserveInventory(@Valid @RequestBody InventoryReserveRequest request) {
        inventoryFacadeService.reserveInventory(request.toCommand());
        return ResponseDto.ok(null);
    }

    @PostMapping("/cancel")
    public ResponseDto<Void> cancelInventory(@Valid @RequestBody InventoryCancelRequest request) {
        inventoryFacadeService.cancelInventory(request.toCommand());
        return ResponseDto.ok(null);
    }
}
