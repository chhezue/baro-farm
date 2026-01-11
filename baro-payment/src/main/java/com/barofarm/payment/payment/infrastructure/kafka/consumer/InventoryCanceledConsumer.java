package com.barofarm.payment.payment.infrastructure.kafka.consumer;

import com.barofarm.payment.common.exception.CustomException;
import com.barofarm.payment.payment.application.dto.request.TossPaymentRefundCommand;
import com.barofarm.payment.payment.domain.Payment;
import com.barofarm.payment.payment.domain.PaymentRepository;
import com.barofarm.payment.payment.domain.PaymentStatus;
import com.barofarm.payment.payment.infrastructure.kafka.consumer.dto.InventoryCanceledEvent;
import com.barofarm.payment.payment.infrastructure.rest.TossPaymentClient;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.barofarm.payment.payment.exception.PaymentErrorCode.PAYMENT_NOT_FOUND;

@Component
@RequiredArgsConstructor
public class InventoryCanceledConsumer {

    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;

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
    }
}

