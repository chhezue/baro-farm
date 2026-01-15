package com.barofarm.ai.chat.presentation;

import com.barofarm.ai.chat.application.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatService chatService;

    // 챗봇 엔드포인트
}
