package com.example.japantravelplanner.poster;

/**
 * 포스터 만들기 요청 (10-14). 본문은 없어도 된다 (전부 기본값).
 *
 * @param style     WATERCOLOR(기본) / RETRO / MINIMAL / STICKER
 * @param character 넣고 싶은 캐릭터 설명 (50자 이하, 예: "안경 쓴 회색 고양이"). 비우면 AI가 자유롭게 꾸민다
 */
public record PosterRequestDto(String style, String character) {
}
