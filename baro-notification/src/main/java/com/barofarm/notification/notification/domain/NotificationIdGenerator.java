package com.barofarm.notification.notification.domain;

/**
 * Notification ID ?앹꽦? "湲곗닠 ?섏〈"?????섎룄
 * -> Domain??吏곸젒 uuid/ulid ?쇱씠釉뚮윭由?紐⑤Ⅴ?꾨줉 ?ы듃濡?遺꾨━
 *
 * Application Service媛 ???명꽣?섏씠???섏〈
 * 援ы쁽 : infrastrucre?먯꽌
 * */
public interface NotificationIdGenerator {

    /***
     * @return ?앹꽦??Notification ID (String)
     */

    String generate();

}
