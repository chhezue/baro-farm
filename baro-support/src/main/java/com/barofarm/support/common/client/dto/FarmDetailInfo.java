package com.barofarm.support.common.client.dto;

import java.util.UUID;

/**
 * Farm 상세 정보 DTO (Feign 응답용)
 * baro-seller의 FarmDetailInfo와 동일한 구조
 */
public record FarmDetailInfo(
    UUID id,
    String name,
    String description,
    String address,
    String phone,
    String email,
    Integer establishedYear,
    String farmSize,
    String cultivationMethod,
    String status,
    UUID sellerId,
    Object image  // 상세 정보 불필요하므로 Object로 처리
) {
}
