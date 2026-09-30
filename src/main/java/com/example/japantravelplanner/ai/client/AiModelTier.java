package com.example.japantravelplanner.ai.client;

/** 하이브리드 모델 전략: 기본은 BASIC, 복잡한 요청과 첫 일정 생성은 ADVANCED */
public enum AiModelTier {
    BASIC,      // gpt-4o-mini
    ADVANCED    // gpt-5.6-terra
}