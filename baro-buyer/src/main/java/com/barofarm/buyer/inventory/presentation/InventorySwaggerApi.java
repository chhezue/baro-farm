package com.barofarm.buyer.inventory.presentation;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Inventory", description = "재고 관련 API")
@RequestMapping("${api.v1}/inventories")
public interface InventorySwaggerApi {

}
