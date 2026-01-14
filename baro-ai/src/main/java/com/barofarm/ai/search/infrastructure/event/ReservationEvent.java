package com.barofarm.ai.search.infrastructure.event;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationEvent {

    private ReservationEventType type;
    private ReservationEventData data;

    public enum ReservationEventType {
        RESERVATION_CREATED,
        RESERVATION_CONFIRMED,
        RESERVATION_CANCELED,
        RESERVATION_COMPLETED
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservationEventData {
        private UUID reservationId;
        private UUID experienceId;
        private UUID buyerId;
        private LocalDate reservedDate;
        private String reservedTimeSlot;
        private Integer headCount;
        private BigInteger totalPrice;
        private String status;
        private Instant createdAt;
    }
}

