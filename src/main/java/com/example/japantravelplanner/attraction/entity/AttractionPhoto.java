package com.example.japantravelplanner.attraction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관광지 대표 사진 (위키미디어 공용). 사진마다 작가·라이선스를 화면에 표시해야 해서 같이 저장한다.
 * source: wikidata(관광지에 붙은 사진) / nearby(근처 사진). 없으면 화면은 Google 사진을 쓴다.
 */
@Embeddable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AttractionPhoto {

    @Column(name = "photo_thumb", length = 1000)
    private String thumb;

    @Column(name = "photo_width")
    private Integer width;

    @Column(name = "photo_height")
    private Integer height;

    @Column(name = "photo_credit", length = 1000)
    private String credit;

    @Column(name = "photo_license", length = 50)
    private String license;

    @Column(name = "photo_license_url", length = 200)
    private String licenseUrl;

    @Column(name = "photo_artist", length = 1000)
    private String artist;

    @Column(name = "photo_source", length = 20)
    private String source;
}
