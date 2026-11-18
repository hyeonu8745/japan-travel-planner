package com.example.japantravelplanner.restaurant.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 체인점 브랜드 ({@code chain_brands}, 86개). 지점(식당 행)마다 같던 한국어 이름·종류·장르·예산을 브랜드 하나에 둔다.
 * 예산은 브랜드 공식 메뉴 가격으로 정한 1인 범위 (OSM 에는 가격이 없다).
 */
@Entity
@Table(name = "chain_brands")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ChainBrand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 브랜드 이름 (일본어, 예: 吉野家)
    @Column(name = "name", nullable = false, length = 60, unique = true)
    private String name;

    // 한국어 이름 (예: 요시노야)
    @Column(name = "name_ko", length = 60)
    private String nameKo;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 10)
    private ChainCategory category;

    // 장르 한국어 (예: 규동)
    @Column(name = "genre_ko", length = 30)
    private String genreKo;

    // 1인 예산 범위 (엔)
    @Column(name = "budget_min")
    private Integer budgetMin;

    @Column(name = "budget_max")
    private Integer budgetMax;

    /** 타베로그식 일본어 장르 (콤마 구분, 한국어 장르로 정함). 모르는 장르면 null */
    public String tabelogGenre() {
        return ChainGenres.of(genreKo);
    }

    /** 끼니(식사) 브랜드인지 (카페·디저트가 아님) */
    public boolean isMeal() {
        return category == ChainCategory.MEAL;
    }
}
