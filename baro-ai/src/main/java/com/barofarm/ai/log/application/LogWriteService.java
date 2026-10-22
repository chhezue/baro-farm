package com.barofarm.ai.log.application;

import com.barofarm.ai.log.domain.UserBehaviorLogDocument;
import com.barofarm.ai.log.infrastructure.elasticsearch.UserBehaviorLogRepository;
import com.barofarm.log.history.model.EventDomain;
import com.barofarm.log.history.model.HistoryEventType;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 사용자 행동 로그를 Elasticsearch `user_behavior_logs` 인덱스에 저장합니다.
 *
 * 장바구니·주문 Kafka 컨슈머 두 곳이 유일한 호출자입니다. 2026-09-09까지는 도메인마다 저장 메서드와
 * 인덱스가 따로 있었으나, 저장하는 내용이 같아 하나로 합쳤습니다({@link UserBehaviorLogDocument}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogWriteService {

    private final UserBehaviorLogRepository userBehaviorLogRepository;

    /**
     * 행동 하나를 저장합니다. 같은 메시지를 다시 처리하면 같은 문서를 덮어쓰므로 중복이 쌓이지 않습니다.
     *
     * @param domain      장바구니인지 주문인지. 문서 ID 앞에 붙어 토픽 간 번호 충돌을 막는다
     * @param messageKey  Kafka 메시지의 위치 (`{파티션}-{오프셋}`, 주문은 뒤에 항목 순번이 더 붙는다)
     * @param userId      행동한 사용자
     * @param productId   행동 대상 상품
     * @param productName 상품명
     * @param eventType   행동 종류 — 취향 벡터의 비중이 이 값으로 정해진다
     * @param occurredAt  행동이 일어난 시각
     */
    public void saveBehaviorLog(EventDomain domain,
                                String messageKey,
                                UUID userId,
                                UUID productId,
                                String productName,
                                HistoryEventType eventType,
                                Instant occurredAt) {
        String documentId = domain.name().toLowerCase(java.util.Locale.ROOT) + "-" + messageKey;

        userBehaviorLogRepository.save(UserBehaviorLogDocument.builder()
            .id(documentId)
            .userId(userId)
            .productId(productId)
            .productName(productName)
            .eventType(eventType)
            .occurredAt(occurredAt)
            .build());

        log.debug("[LOG_WRITE] 행동 로그 저장 - ID: {}, 사용자: {}, 상품: {}", documentId, userId, productName);
    }
}
