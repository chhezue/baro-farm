package com.barofarm.support.common.client.dto;

import java.util.UUID;

/**
 * Farm 목록 정보 DTO (Feign 응답용)
 * baro-seller의 FarmListInfo와 동일한 구조
 */
public record FarmListInfo(
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
