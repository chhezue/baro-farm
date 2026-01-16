package com.barofarm.support.common.client;

import com.barofarm.support.common.client.dto.CustomPage;
import com.barofarm.support.common.client.dto.FarmDetailInfo;
import com.barofarm.support.common.client.dto.FarmListInfo;
import com.barofarm.support.common.client.dto.FarmResponseDto;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Seller Service의 Farm API를 호출하는 Feign 클라이언트
 *
 * Eureka 사용 시: name = "seller-service" (Eureka에 등록된 서비스 이름)
 * Kubernetes 직접 접근 시: name = "baro-seller", url = "http://baro-seller:8085"
 *
 * 현재는 Eureka를 사용하므로 "seller-service" 사용
 */
@FeignClient(name = "seller-service", path = "${api.v1}/farms")
public interface FarmClient {

    /**
     * Farm ID로 Farm 상세 정보 조회
     * 권한 검증을 위해 sellerId를 확인하는 용도로 사용
     *
     * @param farmId Farm ID
     * @return Farm 상세 정보 응답
     */
    @GetMapping("/{id}")
    FarmResponseDto<FarmDetailInfo> getFarmById(@PathVariable("id") UUID farmId);

    /**
     * 사용자 ID로 해당 사용자가 소유한 농장 목록 조회
     * /me 엔드포인트를 사용하여 첫 번째 farm의 ID를 추출
     * (getFarmIdByUserId에서 사용)
     *
     * @param userId 사용자 ID (X-User-Id 헤더로 전달)
     * @param pageable 페이지 정보 (첫 번째 farm만 가져오기 위해 page=0, size=1 사용)
     * @return 농장 목록 응답
     */
    @GetMapping("/me")
    FarmResponseDto<CustomPage<FarmListInfo>> getMyFarmList(
            @RequestHeader("X-User-Id") UUID userId,
            Pageable pageable
    );
}
