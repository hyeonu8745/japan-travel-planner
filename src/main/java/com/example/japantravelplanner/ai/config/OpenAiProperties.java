package com.example.japantravelplanner.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** application.yaml의 app.openai.* 값을 담는 설정 객체 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.openai")
public class OpenAiProperties {

    private String apiKey;
    private String baseUrl = "https://api.openai.com/v1";
    private String basicModel = "gpt-4o-mini";
    private String advancedModel = "gpt-5.6-terra";
    private String advancedReasoningEffort = "low";
    private int maxOutputTokens = 2000;
    private int timeoutSeconds = 60;
}