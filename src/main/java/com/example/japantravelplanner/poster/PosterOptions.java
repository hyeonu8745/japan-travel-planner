package com.example.japantravelplanner.poster;

import com.example.japantravelplanner.global.exception.BadRequestException;

/**
 * 포스터 옵션: 스타일과 캐릭터 설명.
 * 캐릭터는 사용자가 쓴 글이 그림 지시에 들어가므로 길이를 제한하고 따옴표·줄바꿈을 지운 뒤 따옴표 안에 넣는다
 */
public record PosterOptions(PosterStyle style, String character) {

    public static final int CHARACTER_MAX = 50;
    public static final PosterOptions DEFAULT = new PosterOptions(PosterStyle.WATERCOLOR, null);

    /** 요청 → 옵션 (본문이 없으면 기본값: 수채화, 캐릭터 없음) */
    public static PosterOptions from(PosterRequestDto request) {
        if (request == null) return DEFAULT;
        return new PosterOptions(PosterStyle.parse(request.style()), cleanCharacter(request.character()));
    }

    /** 캐릭터 설명 정리: 따옴표·역슬래시·줄바꿈을 지우고 길이 제한 (넘으면 400, 비면 null) */
    static String cleanCharacter(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[\"“”'‘’`\\\\]", "").replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll(" {2,}", " ").strip();
        if (cleaned.isEmpty()) return null;
        if (cleaned.codePointCount(0, cleaned.length()) > CHARACTER_MAX) {
            throw new BadRequestException("캐릭터 설명은 " + CHARACTER_MAX + "자 이하로 적어 주세요.");
        }
        return cleaned;
    }
}
