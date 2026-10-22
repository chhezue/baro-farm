package com.barofarm.ai.search.infrastructure.client;

import com.barofarm.ai.search.infrastructure.client.dto.CatalogProduct;
import com.barofarm.dto.CustomPage;
import com.barofarm.dto.ResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * `baro-shopping`의 상품 목록 API를 호출합니다. 전체 재색인이 원본 상품을 다시 읽을 때 씁니다.
 *
 * <p>평소 색인은 Kafka 상품 이벤트로 이뤄지고 이 클라이언트를 쓰지 않습니다. 이 경로가 필요한 때는
 * <b>매핑이 바뀌어 인덱스를 다시 만들었을 때</b>입니다 — 그때는 이미 지나간 이벤트를 다시 받을 방법이
 * 없어서 상류에 처음부터 다시 물어봐야 합니다. 그전에는 발행 모듈 담당자에게 전체 재발행을 부탁하는 것이
 * 유일한 방법이었습니다.
 *
 * <p>{@code contextId}를 따로 주는 이유는 같은 서비스({@code shopping-service})를 부르는 클라이언트가
 * 이미 있기 때문입니다(장바구니). 이름이 같으면 Spring이 빈을 하나로 보고 거부합니다.
 * 타임아웃은 두 클라이언트가 같은 설정을 공유합니다(`spring.cloud.openfeign.client.config.shopping-service`).
 */
@FeignClient(name = "shopping-service", contextId = "productCatalogClient", path = "/api/v1/products")
public interface ProductCatalogClient {

    /**
     * 상품을 페이지 단위로 읽습니다.
     *
     * @param page 0부터 시작하는 페이지 번호
     * @param size 한 번에 읽을 상품 수
     */
    @GetMapping
    ResponseDto<CustomPage<CatalogProduct>> getProducts(
        @RequestParam("page") int page,
        @RequestParam("size") int size);
}
