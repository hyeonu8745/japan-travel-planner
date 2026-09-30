package com.example.japantravelplanner.chat.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 취향 대화 응답의 JSON 형식 (OpenAI Structured Output, strict 모드) */
public final class PreferenceChatSchema {

    private static final List<String> CATEGORIES = List.of(
            "FOOD_PREFER", "FOOD_AVOID", "ATMOSPHERE", "MOBILITY", "SMOKING", "SCHEDULE", "ETC");

    private PreferenceChatSchema() {
    }

    public static Map<String, Object> format() {
        Map<String, Object> constraintItem = new LinkedHashMap<>();
        constraintItem.put("type", "object");
        constraintItem.put("properties", Map.of(
                "category", Map.of("type", "string", "enum", CATEGORIES),
                "description", Map.of("type", "string")));
        constraintItem.put("required", List.of("category", "description"));
        constraintItem.put("additionalProperties", false);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("reply_to_user", Map.of("type", "string"));
        properties.put("constraints", Map.of("type", "array", "items", constraintItem));
        properties.put("arrival_time", Map.of("type", List.of("string", "null")));
        properties.put("departure_time", Map.of("type", List.of("string", "null")));
        properties.put("food_budget_ratio", Map.of("type", List.of("number", "null")));
        properties.put("summary", Map.of("type", "string"));
        properties.put("ready_to_generate", Map.of("type", "boolean"));
        properties.put("user_wants_generation", Map.of("type", "boolean"));
        properties.put("complexity", Map.of("type", "string", "enum", List.of("LOW", "HIGH")));
        properties.put("complexity_reason", Map.of("type", List.of("string", "null")));
        properties.put("mentioned_travelers", Map.of("type", List.of("integer", "null")));   // ← 추가

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new ArrayList<>(properties.keySet()));
        schema.put("additionalProperties", false);

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "preference_chat");
        format.put("strict", true);
        format.put("schema", schema);
        return format;
    }
}