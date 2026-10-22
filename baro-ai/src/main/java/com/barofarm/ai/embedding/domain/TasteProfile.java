package com.barofarm.ai.embedding.domain;

import java.util.List;
import java.util.UUID;

/**
 * 한 사용자의 취향을 나타내는 계산 결과.
 *
 * <p>2026-09-08까지는 이 값이 `user_profile_embeddings` 인덱스에 문서로 저장돼 있었습니다. 지금은
 * 추천을 요청받은 그 자리에서 행동 로그로부터 만들어 쓰고 버립니다 — 저장하지 않으므로 이 타입에는
 * `@Document`도 `@Id`도 없습니다.
 *
 * <p>저장을 그만둔 이유는 저장이 주는 이득보다 딸려 오는 문제가 컸기 때문입니다. 미리 만들어 두려면
 * 장바구니·주문 이벤트마다 프로필을 다시 계산해야 했고(이벤트 1건당 Elasticsearch 왕복 4회),
 * 같은 사용자의 이벤트 둘이 동시에 처리되면 나중 것이 먼저 것을 덮어써 한쪽 행동이 사라질 수 있었으며,
 * 방금 담은 상품이 추천에 반영되기까지 시간이 걸렸습니다. 반면 계산 자체는 벡터 열 개를 더하는 정도라
 * 요청 때마다 해도 사람이 느낄 만한 차이가 없습니다.
 *
 * @param vector               행동 로그로 만든 768차원 취향 벡터. 길이가 1로 맞춰져 있어 코사인 유사도 비교에 바로 쓴다
 * @param experiencedProductIds 이 벡터를 만드는 데 쓰인 상품들. 이미 담아 봤거나 산 상품이라 추천에서 뺀다
 */
public record TasteProfile(
    float[] vector,
    List<UUID> experiencedProductIds
) { }
