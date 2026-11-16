package com.example.japantravelplanner.facility.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FacilityTest {

    @Test
    void typeCodesMatchDataFile() {
        assertThat(FacilityType.ofCode("publicBath")).contains(FacilityType.PUBLIC_BATH);
        assertThat(FacilityType.ofCode("locker")).contains(FacilityType.LOCKER);
        assertThat(FacilityType.ofCode("tram_stop")).isEmpty();
    }

    @Test
    void displayNamePrefersKoreanThenJapanese() {
        assertThat(Facility.builder().name("Lawson").nameJa("ローソン").nameKo("로손").build().displayName()).isEqualTo("로손");
        assertThat(Facility.builder().name("Lawson").nameJa("ローソン").build().displayName()).isEqualTo("ローソン");
        assertThat(Facility.builder().name("Lawson").build().displayName()).isEqualTo("Lawson");
        assertThat(Facility.builder().nameKo(" ").nameEn("Locker").build().displayName()).isEqualTo("Locker");
    }
}
