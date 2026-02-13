package com.barofarm.notification.notification_delivery.infrastructure.util;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * [JSON 吏곷젹????쭅?ы솕 ?좏떥]
 *
 * ?꾩슂??:
 * - Kafka payload瑜?STRING?쇰줈 諛쏆쑝硫?Consumer?먯꽌 DTO濡??뚯떛
 * - ObjectMapper ?ㅼ젙(unknown ?꾨뱶 臾댁떆 ??????怨녹뿉???듭씪?섍린 ?꾪빐??
 *
 * 二쇱쓽:
 * - "Producer(notification)"? "Consumer(notification_delivery)"媛
 *   payload 踰꾩쟾???쎄컙 ?щ씪吏????덉뼱??
 *   unknown field 臾댁떆 ?ㅼ젙??耳쒕뒗 寃껋씠 留ㅼ슦 以묒슂?섎떎.
 * */
public class Jsons {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        // payload???꾨뱶媛 ?섏뼱?섎룄 consumer媛 二쎌? ?딅룄濡??덉쟾?섍쾶.
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Jsons() {}

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON parsing failed: " + e.getMessage(), e);
        }
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON serialization failed: " + e.getMessage(), e);
        }
    }
}
