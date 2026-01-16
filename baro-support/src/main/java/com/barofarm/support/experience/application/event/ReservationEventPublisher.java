package com.barofarm.support.experience.application.event;

import com.barofarm.support.event.ReservationEvent;
import com.barofarm.support.event.ReservationEvent.ReservationEventType;
import com.barofarm.support.experience.domain.Reservation;
import com.barofarm.support.experience.infrastructure.kafka.ReservationEventProducer;
import java.time.Instant;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 예약 도메인을 Kafka 이벤트로 변환하고, 어떤 이벤트를 발행할지 결정 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventPublisher {

    private final ReservationEventProducer producer;

    /**
     * 예약 생성 시 발행
     *
     * @param reservation 예약 엔티티
     */
    public void publishReservationCreated(Reservation reservation) {
        log.info(
            "📨 [EVENT_PUBLISHER] Building RESERVATION_CREATED - Reservation ID: {}, Experience ID: {}, Buyer ID: {}",
            reservation.getReservationId(), reservation.getExperienceId(), reservation.getBuyerId());
        
        ReservationEvent event = buildEvent(ReservationEventType.RESERVATION_CREATED, reservation);
        log.info("📨 [EVENT_PUBLISHER] Event built successfully - Type: {}, Reservation ID: {}",
            event.getType(), event.getData().getReservationId());
        
        producer.send(event);
    }

    /**
     * 예약 상태 변경 시 발행
     *
     * @param reservation 예약 엔티티
     */
    public void publishReservationStatusChanged(Reservation reservation) {
        ReservationEventType eventType = switch (reservation.getStatus().name()) {
            case "CONFIRMED" -> ReservationEventType.RESERVATION_CONFIRMED;
            case "CANCELED" -> ReservationEventType.RESERVATION_CANCELED;
            case "COMPLETED" -> ReservationEventType.RESERVATION_COMPLETED;
            default -> null; // REQUESTED 상태는 생성 시에만 발행
        };

        if (eventType != null) {
            log.info(
                "📨 [EVENT_PUBLISHER] Building {} - Reservation ID: {}",
                eventType, reservation.getReservationId());
            
            ReservationEvent event = buildEvent(eventType, reservation);
            producer.send(event);
        }
    }

    private ReservationEvent buildEvent(ReservationEventType type, Reservation reservation) {
        ReservationEvent.ReservationEventData data = ReservationEvent.ReservationEventData.builder()
            .reservationId(reservation.getReservationId())
            .experienceId(reservation.getExperienceId())
            .buyerId(reservation.getBuyerId())
            .reservedDate(reservation.getReservedDate())
            .reservedTimeSlot(reservation.getReservedTimeSlot())
            .headCount(reservation.getHeadCount())
            .totalPrice(reservation.getTotalPrice())
            .status(reservation.getStatus().name())
            .createdAt(reservation.getCreatedAt() != null 
                ? reservation.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant() 
                : Instant.now())
            .build();

        return ReservationEvent.builder()
            .type(type)
            .data(data)
            .build();
    }
}

