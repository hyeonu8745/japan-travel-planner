package com.example.japantravelplanner.dataimport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** clean_transit.json → transit_stops·transit_routes 행 (11-22) */
class TransitImporterTest {

    @Test
    void 파일의_역과_노선을_읽고_모르는_필드는_무시한다(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("clean_transit.json");
        Files.writeString(file, """
                {"stops": {"n1": {"name": "新宿", "nameEn": "Shinjuku", "nameKo": "신주쿠", "lat": 35.69, "lng": 139.70, "extra": 1},
                           "n2": {"name": "四ツ谷", "nameEn": null, "nameKo": null, "lat": 35.68, "lng": 139.73}},
                 "routes": [{"id": "r1", "mode": "subway", "ref": "M", "line": "丸ノ内線", "name": "丸ノ内線 (池袋 → 荻窪)",
                             "nameEn": null, "operator": "東京メトロ", "colour": "#F62E36", "stops": ["n1", "n2"]}]}
                """, StandardCharsets.UTF_8);

        TransitImporter.TransitFile read = TransitImporter.read(file);

        assertThat(read.stops()).containsOnlyKeys("n1", "n2");
        assertThat(read.stops().get("n1").nameKo()).isEqualTo("신주쿠");
        assertThat(read.routes()).singleElement().satisfies(r -> {
            assertThat(r.line()).isEqualTo("丸ノ内線");
            assertThat(r.stops()).containsExactly("n1", "n2");
        });
    }

    @Test
    void 행으로_바꿀_때_정차_순서는_쉼표로_잇고_긴_값은_열_길이에_맞춰_자른다() {
        Object[] route = TransitImporter.routeArgs(new TransitImporter.RouteRow("r1", "bus", "1".repeat(50), "線",
                "name", null, "会社", "#123456", List.of("a", "b", "c")));
        assertThat(route[8]).isEqualTo("a,b,c");
        assertThat((String) route[2]).hasSize(40);

        Object[] stop = TransitImporter.stopArgs("s1", new TransitImporter.StopRow("駅".repeat(120), null, null, 35.0, 139.0));
        assertThat((String) stop[1]).hasSize(100);
        assertThat(stop).endsWith(35.0, 139.0);
    }
}
