package com.example.japantravelplanner.itinerary.narration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 하루 설명 응답의 JSON 형식 (OpenAI Structured Output, strict 모드) */
public final class NarrationSchema {

    private NarrationSchema() {
    }

    public static Map<String, Object> format() {
        Map<String, Object> dayItem = new LinkedHashMap<>();
        dayItem.put("type", "object");
        dayItem.put("properties", Map.of(
                "day", Map.of("type", "integer"),
                "description", Map.of("type", "string")));
        dayItem.put("required", List.of("day", "description"));
        dayItem.put("additionalProperties", false);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of("days", Map.of("type", "array", "items", dayItem)));
        schema.put("required", List.of("days"));
        schema.put("additionalProperties", false);

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "itinerary_narration");
        format.put("strict", true);
        format.put("schema", schema);
        return format;
    }
}