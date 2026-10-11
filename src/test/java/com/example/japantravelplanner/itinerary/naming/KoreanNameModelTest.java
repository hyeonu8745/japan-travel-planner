package com.example.japantravelplanner.itinerary.naming;

import com.example.japantravelplanner.ai.client.AiModelTier;
import com.example.japantravelplanner.ai.client.AiResult;
import com.example.japantravelplanner.ai.client.OpenAiClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 한국어 이름을 만들 모델 (10-11: 기본 모델이 増上寺를 "조우장사", Chopsticks를 "초밥"으로 만들어 고급 모델로) */
class KoreanNameModelTest {

    private final OpenAiClient openAiClient = mock(OpenAiClient.class);
    private final KoreanNameProperties props = new KoreanNameProperties();
    private final KoreanNameTranslator translator =
            new KoreanNameTranslator(openAiClient, JsonMapper.builder().build(), props);

    private void aiReturns(String json) {
        when(openAiClient.generateJson(any(), anyString(), anyString(), anyMap(), anyInt(), anyDouble()))
                .thenReturn(new AiResult(json, "model", 1, 1, 0, 1));
    }

    @Test
    void 고급_모델을_켜면_고급_모델로_추론_토큰까지_넉넉하게_부른다() {
        props.setUseAdvancedModel(true);
        aiReturns("{\"items\":[{\"id\":\"a1\",\"name_ko\":\"조조지\"}]}");

        Map<String, String> names = translator.translate(Map.of("a1", "増上寺"));

        assertThat(names).containsEntry("a1", "조조지");
        verify(openAiClient).generateJson(eq(AiModelTier.ADVANCED), anyString(), anyString(), anyMap(),
                eq((200 + KoreanNameTranslator.OUTPUT_TOKENS_PER_NAME) * 4), anyDouble());
    }

    @Test
    void 끄면_기본_모델() {
        props.setUseAdvancedModel(false);
        aiReturns("{\"items\":[]}");

        translator.translate(Map.of("a1", "増上寺"));

        verify(openAiClient).generateJson(eq(AiModelTier.BASIC), anyString(), anyString(), anyMap(), anyInt(), anyDouble());
    }

    @Test
    void 프롬프트에_뜻_번역과_한국_한자음을_막는_예시가_있다() {
        assertThat(KoreanNamePrompt.INSTRUCTIONS).contains("増上寺 → 조조지").contains("Chopsticks Studio Ginza → 찹스틱스 스튜디오 긴자");
    }
}
