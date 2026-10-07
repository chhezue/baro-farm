package com.barofarm.ai.recommend.infrastructure.client;

import com.barofarm.ai.recommend.infrastructure.client.dto.CartInfo;
import com.barofarm.dto.ResponseDto;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 쇼핑 서비스(shopping-service)의 장바구니 API를 호출하기 위한 Spring Cloud OpenFeign 클라이언트 인터페이스입니다.
 *
 * [통신 규격 및 주의사항]
 * - 대상 서비스: `baro-shopping`의 `/api/v1/carts`. 이 모듈은 Eureka를 쓰지 않으므로 클라이언트 이름(`shopping-service`)으로
 *   주소를 찾지 않고, `spring.cloud.openfeign.client.config.shopping-service.url`이 정한 주소를 그대로 호출합니다
 *   (기본값 `http://baro-shopping:8080`, 환경변수 `SHOPPING_SERVICE_URL`로 덮어씀)
 * - 인증/식별: 게이트웨이 또는 내부 호출 시 전달되는 `X-User-Id` 헤더를 통해 사용자 UUID를 전달합니다.
 * - 응답 봉투(Envelope): shopping-service의 모든 응답은 `{status, data, message}` 형태의 `ResponseDto`로 감싸져 반환되므로,
 *   반환 타입을 `ResponseDto<CartInfo>`로 지정하여 data 필드를 올바르게 언래핑할 수 있도록 합니다.
 */
@FeignClient(
    name = "shopping-service",
    path = "/api/v1/carts"
)
public interface CartClient {

    /**
     * 특정 사용자의 현재 장바구니 정보 및 담긴 상품 항목들을 조회합니다.
     *
     * @param userId 조회할 사용자의 고유 UUID (HTTP Header: X-User-Id)
     * @return 장바구니 상세 정보(CartInfo)가 포함된 표준 응답 봉투 DTO
     */
    @GetMapping
    ResponseDto<CartInfo> getCart(@RequestHeader("X-User-Id") UUID userId);
}
