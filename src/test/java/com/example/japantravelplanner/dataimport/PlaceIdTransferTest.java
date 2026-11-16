package com.example.japantravelplanner.dataimport;

import com.example.japantravelplanner.dataimport.PlaceIdTransfer.NewPlace;
import com.example.japantravelplanner.dataimport.PlaceIdTransfer.OldPlace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceIdTransferTest {

    @Test
    void sameNameNearbyGetsPlaceId() {
        Map<String, String> m = PlaceIdTransfer.match(
                List.of(new OldPlace("G1", "浅草寺", 35.7148, 139.7967)),
                List.of(new NewPlace("way/1", "金龍山 浅草寺", "Senso-ji", 35.7147, 139.7966)));
        assertThat(m).containsEntry("way/1", "G1");
    }

    @Test
    void englishNameAlsoMatches() {
        Map<String, String> m = PlaceIdTransfer.match(
                List.of(new OldPlace("G2", "Tokyo Skytree", 35.7101, 139.8107)),
                List.of(new NewPlace("way/2", "東京スカイツリー", "Tokyo Skytree", 35.7100, 139.8107)));
        assertThat(m).containsEntry("way/2", "G2");
    }

    @Test
    void farOrDifferentNameIsNotMatched() {
        Map<String, String> m = PlaceIdTransfer.match(
                List.of(new OldPlace("G3", "浅草寺", 35.7148, 139.7967),
                        new OldPlace("G4", "上野動物園", 35.7164, 139.7714)),
                List.of(new NewPlace("way/3", "浅草寺", null, 35.6800, 139.7600),
                        new NewPlace("way/4", "国立科学博物館", null, 35.7163, 139.7715)));
        assertThat(m).isEmpty();
    }

    @Test
    void eachPlaceUsedOnceClosestWins() {
        Map<String, String> m = PlaceIdTransfer.match(
                List.of(new OldPlace("G5", "上野公園", 35.7146, 139.7732)),
                List.of(new NewPlace("way/5", "上野恩賜公園", null, 35.7160, 139.7740),
                        new NewPlace("way/6", "上野公園", null, 35.7146, 139.7733)));
        assertThat(m).hasSize(1).containsEntry("way/6", "G5");
    }

    @Test
    void exactNameMatchesFartherForBigParks() {
        Map<String, String> m = PlaceIdTransfer.match(
                List.of(new OldPlace("SHIBA", "芝公園", 35.6560, 139.7500),
                        new OldPlace("PART", "公園通り", 35.6560, 139.7500)),
                List.of(new NewPlace("way/8", "芝公園", null, 35.6600, 139.7480),
                        new NewPlace("way/9", "渋谷公園通り", null, 35.6600, 139.7480)));
        assertThat(m).containsEntry("way/8", "SHIBA").doesNotContainKey("way/9");
    }

    @Test
    void exactNameBeatsCloserPartialName() {
        Map<String, String> m = PlaceIdTransfer.match(
                List.of(new OldPlace("TOP", "東京タワー トップデッキ", 35.65860, 139.74540),
                        new OldPlace("TOWER", "東京タワー", 35.65855, 139.74545)),
                List.of(new NewPlace("way/7", "東京タワー", null, 35.65860, 139.74540)));
        assertThat(m).containsEntry("way/7", "TOWER");
    }

    @Test
    void normalizeIgnoresWidthSpacesAndDots() {
        assertThat(PlaceIdTransfer.normalize("ＴＯＫＹＯ　タワー")).isEqualTo(PlaceIdTransfer.normalize("tokyo タワー"));
        assertThat(PlaceIdTransfer.sameName("a", "ab")).isFalse();
    }
}
