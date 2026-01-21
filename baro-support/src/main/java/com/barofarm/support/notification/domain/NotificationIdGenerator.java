package com.barofarm.support.notification.domain;

/**
 * Notification ID 생성은 "기술 의존"이 될 수도
 * -> Domain이 직접 uuid/ulid 라이브러리 모르도록 포트로 분리
 *
 * Application Service가 이 인터페이스 의존
 * 구현 : infrastrucre에서
 * */
public interface NotificationIdGenerator {

    /***
     * @return 생성된 Notification ID (String)
     */

    String generate();

}
