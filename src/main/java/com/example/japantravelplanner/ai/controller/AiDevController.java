package com.example.japantravelplanner.ai.controller;

import com.example.japantravelplanner.ai.client.AiModelTier;
import com.example.japantravelplanner.ai.client.AiResult;
import com.example.japantravelplanner.ai.client.OpenAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** OpenAI 연결 확인용 임시 API. 취향 대화 API가 완성되면 삭제한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dev")
public class AiDevController {

    private final OpenAiClient openAiClient;

    @GetMapping("/ai-ping")
    public AiResult ping(@RequestParam(defaultValue = "BASIC") AiModelTier tier) {
        return openAiClient.generate(
                tier,
                "너는 도쿄 여행 도우미야. 한국어로 한 문장으로만 답해.",
                "도쿄 여행을 한 문장으로 소개해줘."
        );
    }
}