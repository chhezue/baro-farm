package com.barofarm.ai.search.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.barofarm.ai.search.application.ProductIndexService;
import com.barofarm.ai.search.application.dto.product.ProductIndexRequest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.kafka.listener.BatchListenerFailedException;

/**
 * 묶음 소비의 순서 규칙을 고정한다 — 색인은 모아서 한 번, 삭제를 만나면 모인 색인을 먼저 내보낸 뒤 삭제한다.
 * 실패는 레코드 위치를 담아 던져 컨테이너가 그 자리부터 재시도하게 한다.
 */
class ProductEventConsumerTest {

    private ProductIndexService indexService;
    private ProductEventConsumer consumer;

    @BeforeEach
    void setUp() {
        indexService = mock(ProductIndexService.class);
        consumer = new ProductEventConsumer(indexService);
    }

    @Test
    @DisplayName("생성·수정 이벤트 3개는 indexProducts 1회로 모아서 색인한다")
    void createdAndUpdated_areBatched() {
        consumer.onMessages(List.of(
            event(ProductEvent.ProductEventType.PRODUCT_CREATED, "사과"),
            event(ProductEvent.ProductEventType.PRODUCT_UPDATED, "배"),
            event(ProductEvent.ProductEventType.PRODUCT_CREATED, "감귤")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductIndexRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(indexService, times(1)).indexProducts(captor.capture());
        assertThat(captor.getValue()).extracting(ProductIndexRequest::productName).containsExactly("사과", "배", "감귤");
    }

    @Test
    @DisplayName("같은 상품의 생성 → 삭제 순서에서 색인이 삭제보다 먼저 나간다")
    void delete_flushesPendingFirst() {
        UUID id = UUID.randomUUID();
        consumer.onMessages(List.of(
            event(ProductEvent.ProductEventType.PRODUCT_CREATED, "사과", id),
            event(ProductEvent.ProductEventType.PRODUCT_DELETED, "사과", id),
            event(ProductEvent.ProductEventType.PRODUCT_CREATED, "배")));

        InOrder order = inOrder(indexService);
        order.verify(indexService).indexProducts(anyList());   // 사과
        order.verify(indexService).deleteProduct(id);
        order.verify(indexService).indexProducts(anyList());   // 배
    }

    @Test
    @DisplayName("본문이 없는 이벤트(tombstone)는 건너뛰고 나머지는 처리한다")
    void nullEvents_areSkipped() {
        consumer.onMessages(Arrays.asList(null, event(ProductEvent.ProductEventType.PRODUCT_CREATED, "사과")));

        verify(indexService, times(1)).indexProducts(anyList());
        verify(indexService, never()).deleteProduct(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("색인이 실패하면 실패한 묶음의 첫 레코드 위치를 담은 예외를 던진다 — 그 앞은 커밋되고 거기서부터 재시도")
    void indexFailure_carriesFirstPendingIndex() {
        UUID deleted = UUID.randomUUID();
        // [DELETED(0), CREATED(1), CREATED(2)] — 색인 묶음은 1번부터 시작한다
        doThrow(new RuntimeException("es down")).when(indexService).indexProducts(anyList());

        assertThatThrownBy(() -> consumer.onMessages(List.of(
                event(ProductEvent.ProductEventType.PRODUCT_DELETED, "무", deleted),
                event(ProductEvent.ProductEventType.PRODUCT_CREATED, "사과"),
                event(ProductEvent.ProductEventType.PRODUCT_CREATED, "배"))))
            .isInstanceOf(BatchListenerFailedException.class)
            .satisfies(e -> assertThat(((BatchListenerFailedException) e).getIndex()).isEqualTo(1))
            .hasCauseInstanceOf(RuntimeException.class);
        verify(indexService).deleteProduct(deleted);
    }

    @Test
    @DisplayName("삭제가 실패하면 그 레코드 위치를 담은 예외를 던진다 — 앞서 모아 둔 색인은 이미 내보낸 뒤다")
    void deleteFailure_carriesRecordIndex() {
        UUID broken = UUID.randomUUID();
        doThrow(new RuntimeException("es down")).when(indexService).deleteProduct(broken);

        assertThatThrownBy(() -> consumer.onMessages(List.of(
                event(ProductEvent.ProductEventType.PRODUCT_CREATED, "사과"),
                event(ProductEvent.ProductEventType.PRODUCT_DELETED, "무", broken),
                event(ProductEvent.ProductEventType.PRODUCT_CREATED, "배"))))
            .isInstanceOf(BatchListenerFailedException.class)
            .satisfies(e -> assertThat(((BatchListenerFailedException) e).getIndex()).isEqualTo(1));
        // 0번(사과)은 삭제 전에 색인됐고, 2번(배)은 컨테이너가 1번부터 다시 넘기므로 이번 시도에서는 색인하지 않는다
        verify(indexService, times(1)).indexProducts(anyList());
    }

    private static ProductEvent event(ProductEvent.ProductEventType type, String name) {
        return event(type, name, UUID.randomUUID());
    }

    private static ProductEvent event(ProductEvent.ProductEventType type, String name, UUID id) {
        return new ProductEvent(type, new ProductEvent.ProductEventData(
            id, name, null, "AGRI_X", "과일", 1000L, "ON_SALE", null));
    }
}
