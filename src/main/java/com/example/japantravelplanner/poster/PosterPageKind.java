package com.example.japantravelplanner.poster;

/**
 * 포스터 장 종류 (일러스트 지도 / 상세 일정표).
 */
public enum PosterPageKind {
    /** 일러스트 지도 포스터 (1장) */
    COVER,
    /** 상세 일정표 (날 수에 따라 여러 장, 글자까지 이미지 AI가 그림). detailMode TEXT 일 때만 */
    DETAIL,
    /**
     * 상세 일정표 장식 배경 (11-22, 기본). 글자 없는 A4 테두리·머리띠·계절 장식 한 장.
     * 프론트가 인쇄용 일정표 페이지에 이 그림을 배경으로 깔고 일정 내용을 글자로 올린다 (글자가 틀리지 않게)
     */
    DECORATION
}
