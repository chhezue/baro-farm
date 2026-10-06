package com.barofarm.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * baro-ai 마이크로서비스 애플리케이션의 구동 및 부트스트래핑을 담당하는 메인 진입점 클래스입니다.
 *
 * [주요 설정 및 활성화 기능]
 * - `@SpringBootApplication`: Spring Boot 컴포넌트 스캔, 자동 설정(Auto-Configuration) 활성화
 * - `@EnableFeignClients`: 장바구니 서비스(shopping-service) 등 외부 서비스와의 통신을 위한 OpenFeign 클라이언트 인터페이스 활성화
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.barofarm.ai")
public class AiApplication {

    /**
     * Spring Boot 애플리케이션을 구동하는 메인 메서드.
     *
     * @param args 애플리케이션 실행 커맨드라인 인자
     */
    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
