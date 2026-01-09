package com.barofarm.log.history.aspect;

import com.barofarm.config.HistoryLogProperties;
import com.barofarm.log.history.annotation.TrackHistory;
import com.barofarm.log.history.mapper.HistoryPayloadMapper;
import com.barofarm.log.history.model.HistoryEventType;
import com.barofarm.log.history.model.HistoryEnvelope;
import com.barofarm.log.history.writer.HistoryLogWriter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
public class HistoryAspect {

    private static final Logger INTERNAL_LOG =
        LoggerFactory.getLogger(HistoryAspect.class);
    private final Map<HistoryEventType, HistoryPayloadMapper> mapperMap;
    private final HistoryLogWriter writer;
    private final String userIdHeader;

    public HistoryAspect(
        List<HistoryPayloadMapper> mappers,
        HistoryLogWriter writer,
        HistoryLogProperties properties
    ) {
        this.mapperMap = mappers.stream()
            .collect(Collectors.toMap(
                HistoryPayloadMapper::supports,
                m -> m,
                (existing, duplicate) -> {
                    INTERNAL_LOG.warn(
                        "Duplicate HistoryPayloadMapper for type {}: {} vs {}. Keeping the first.",
                        existing.supports(),
                        existing.getClass().getName(),
                        duplicate.getClass().getName()
                    );
                    return existing;
                }
            ));
        this.writer = writer;
        this.userIdHeader = properties.getUserIdHeader();
    }

    @Around("@annotation(trackHistory)")
    public Object record(ProceedingJoinPoint pjp, TrackHistory trackHistory) throws Throwable {
        Object[] args = pjp.getArgs();

        HistoryEventType type = trackHistory.value();
        HistoryPayloadMapper mapper = mapperMap.get(type);
        if (mapper == null) {
            INTERNAL_LOG.warn("No HistoryPayloadMapper found for type: {}", type);
            return pjp.proceed();
        }

        Map<String, Object> payload = null;
        if (mapper.mapBeforeProceed()) {
            payload = mapper.payload(args, null);
        }

        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            // 정책: 실패한 요청은 history 발행하지 않음 (AOP가 실패했다고 해서 주문/장바구니 로직이 실패하는 것 X)
            throw t;
        }

        if (!mapper.mapBeforeProceed()) {
            payload = mapper.payload(args, result);
        }

        UUID userId = resolveUserIdFromHeader();
        HistoryEnvelope<Map<String, Object>> envelope = new HistoryEnvelope<>(
                type,
                OffsetDateTime.now(),
                userId,
                payload
        );

        writer.write(type, envelope);
        return result;
    }

    private UUID resolveUserIdFromHeader() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }

        HttpServletRequest request = attrs.getRequest();
        String userId = request.getHeader(userIdHeader);
        if (userId == null || userId.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
