package com.example.japantravelplanner.poster;

import java.util.Locale;

/** 포스터 그림 스타일 (10-14). 모든 장에 같은 스타일을 쓴다. 안 고르면 수채화 */
public enum PosterStyle {

    WATERCOLOR("수채화", "hand-drawn soft pastel watercolor and ink"),
    RETRO("레트로 여행 포스터", "retro vintage travel poster with flat bold shapes, limited warm colors and a slight paper grain"),
    MINIMAL("미니멀 라인", "minimal clean line drawing with thin outlines, lots of white space and a few soft accent colors"),
    STICKER("귀여운 스티커", "cute sticker-style illustrations with thick white outlines and bright cheerful colors");

    private final String label;
    private final String prompt;

    PosterStyle(String label, String prompt) {
        this.label = label;
        this.prompt = prompt;
    }

    public String label() {
        return label;
    }

    /** 그림 지시 문장 (영어) */
    public String prompt() {
        return prompt;
    }

    /** 비어 있으면 수채화, 모르는 값이면 400 */
    public static PosterStyle parse(String value) {
        if (value == null || value.isBlank()) return WATERCOLOR;
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("포스터 스타일은 WATERCOLOR, RETRO, MINIMAL, STICKER 중에서 골라 주세요.");
        }
    }
}
