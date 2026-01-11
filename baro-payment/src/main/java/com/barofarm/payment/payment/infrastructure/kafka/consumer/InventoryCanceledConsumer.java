package com.barofarm.payment.payment.infrastructure.kafka.consumer;

import com.barofarm.payment.common.exception.CustomException;
import com.barofarm.payment.payment.application.dto.request.TossPaymentRefundCommand;
import com.barofarm.payment.payment.domain.Payment;
import com.barofarm.payment.payment.domain.PaymentOutboxEvent;
import com.barofarm.payment.payment.domain.PaymentOutboxEventRepository;
import com.barofarm.payment.payment.domain.PaymentRepository;
import com.barofarm.payment.payment.domain.PaymentStatus;
import com.barofarm.payment.payment.exception.PaymentErrorCode;
import com.barofarm.payment.payment.infrastructure.kafka.consumer.dto.InventoryCanceledEvent;
import com.barofarm.payment.payment.infrastructure.kafka.producer.dto.PaymentCanceledEvent;
import com.barofarm.payment.payment.infrastructure.rest.TossPaymentClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.barofarm.payment.payment.exception.PaymentErrorCode.PAYMENT_NOT_FOUND;

@Component
@RequiredArgsConstructor
public class InventoryCanceledConsumer {

    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentOutboxEventRepository paymentOutboxEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "inventory-canceled",
        groupId = "payment-service.inventory-canceled",
        properties = {
            "spring.json.value.default.type=com.barofarm.payment.payment.infrastructure.kafka.consumer.dto.InventoryCanceledEvent"
        }
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public void handle(InventoryCanceledEvent event) {
        Optional<Payment> optionalPayment = paymentRepository.findByOrderId(event.orderId());
        if (optionalPayment.isEmpty()) {
            throw new CustomException(PAYMENT_NOT_FOUND);
        }

        Payment payment = optionalPayment.get();
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }

        if (payment.getStatus() != PaymentStatus.CONFIRMED) {
            return;
        }

        TossPaymentRefundCommand command = new TossPaymentRefundCommand(
            payment.getPaymentKey(),
            "Order canceled: " + payment.getOrderId()
        );
        tossPaymentClient.refund(command);
        payment.refund();

        PaymentCanceledEvent canceledEvent = new PaymentCanceledEvent(
            event.orderId(),
            payment.getAmount()
        );
        try {
            String payload = objectMapper.writeValueAsString(canceledEvent);

            PaymentOutboxEvent outbox = PaymentOutboxEvent.pending(
                "PAYMENT",
                payment.getId().toString(),
                "payment-canceled",
                payment.getOrderId(),
                payload
            );
            paymentOutboxEventRepository.save(outbox);
        } catch (JsonProcessingException e) {
            throw new CustomException(PaymentErrorCode.OUTBOX_SERIALIZATION_FAILED);
        }
    }
}
