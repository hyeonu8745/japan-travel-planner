package com.example.japantravelplanner.dataimport;

import com.example.japantravelplanner.dataimport.ExpansionRows.AttractionRow;
import com.example.japantravelplanner.dataimport.ExpansionRows.ChainRow;
import com.example.japantravelplanner.dataimport.ExpansionRows.FacilityRow;
import com.example.japantravelplanner.dataimport.ExpansionRows.NightRow;
import com.example.japantravelplanner.dataimport.ExpansionRows.OfficialRow;
import com.example.japantravelplanner.dataimport.ExpansionRows.PhotoRow;
import com.example.japantravelplanner.dataimport.ExpansionRows.TabelogRow;
import com.example.japantravelplanner.restaurant.entity.RestaurantSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpansionDataImporterTest {

    static TabelogRow tabelog(String source, String city, String address) {
        return new TabelogRow("13139364", "鳥酎 飯田橋", city, 35.70, 139.74, "banchi", false, address, 3.46, 119,
                List.of("焼鳥", "居酒屋"), "～￥999", null, 999, "￥3,000～￥3,999", 3000, 3999, null, "日曜",
                source, List.of("SUN"), false, false, true, "飯田橋駅から190m", 190, false, false, false, true,
                "Bronze", false);
    }

    @Test
    void tabelogArgsMatchPlaceholders() {
        Object[] args = ExpansionDataImporter.tabelogArgs(tabelog("kaggle", "tokyo", "東京都千代田区飯田橋4-7-11"));
        assertThat(args).hasSize(placeholders(ExpansionDataImporter.TABELOG_SQL));
        // 장르는 예전 데이터처럼 쉼표, 주소에서 구 이름, 도시·출처는 enum 이름
        assertThat(args[4]).isEqualTo("焼鳥,居酒屋");
        assertThat(args[15]).isEqualTo("東京");
        assertThat(args[16]).isEqualTo("千代田区");
        assertThat(args[17]).isEqualTo(139.74);
        assertThat(args[18]).isEqualTo(35.70);
        assertThat(args[19]).isEqualTo("TOKYO");
        assertThat(args[20]).isEqualTo("KAGGLE");
        assertThat(args[24]).isEqualTo("SUN");
    }

    @Test
    void otherArgsMatchPlaceholders() {
        ChainRow chain = new ChainRow("node/1", "吉野家 渋谷店", "吉野家", "요시노야", "MEAL", "규동", 500, 1000,
                35.66, 139.70, "tokyo", "24/7");
        assertThat(ExpansionDataImporter.chainArgs(chain, 7L)).hasSize(placeholders(ExpansionDataImporter.CHAIN_SQL));

        AttractionRow full = new AttractionRow("way/1", "清水寺", "Kiyomizu-dera", "기요미즈데라", "TEMPLE", 34.99,
                135.78, "kyoto", "Q221716", 77, "Mo-Su 06:00-18:00", null, "yes", null, true,
                new NightRow("야간 개장", "18:00-21:00", "400엔", "기간 외", "https://x"), true,
                new OfficialRow("운영", "불필요", "무휴", "06:00~18:00", "400엔", "x"),
                new PhotoRow("https://t", 800, 528, "https://c", "CC BY-SA 4.0", "https://l", "A", "wikidata"));
        assertThat(ExpansionDataImporter.attractionArgs(full)).hasSize(placeholders(ExpansionDataImporter.ATTRACTION_SQL));
        AttractionRow bare = new AttractionRow("way/2", "公園", null, null, "PARK", 35, 139, "tokyo", null, null,
                null, null, null, null, false, null, false, null, null);
        assertThat(ExpansionDataImporter.attractionArgs(bare)).hasSize(placeholders(ExpansionDataImporter.ATTRACTION_SQL));

        FacilityRow f = new FacilityRow("node/9", "ローソン", null, null, null, 35, 139, null, "24/7", null, null,
                "convenience", "osaka", "ローソン", null, null, null, null, null, null, null, null);
        Object[] fa = ExpansionDataImporter.facilityArgs(f);
        assertThat(fa).hasSize(placeholders(ExpansionDataImporter.FACILITY_SQL));
        assertThat(fa[1]).isEqualTo("CONVENIENCE");
        assertThat(fa[2]).isEqualTo("OSAKA");
    }

    @Test
    void famousOldTokyoOnlyWhenUnmatchedPopularAndNotSensitive() {
        var ame = ExpansionDataImporter.oldTokyoRow(new String[]{"台東区", "G1", "アメ横商店街", "東京都台東区上野4", "35.71",
                "139.77", "4.2", "12260.0", "tourist_attraction,market"});
        var small = ExpansionDataImporter.oldTokyoRow(new String[]{"台東区", "G2", "小さな店", "", "35.71", "139.77",
                "4.8", "120.0", ""});
        var meiji = ExpansionDataImporter.oldTokyoRow(new String[]{"渋谷区", "G3", "明治神宮", "", "35.67", "139.69",
                "4.6", "53567.0", "shinto_shrine"});
        var matched = ExpansionDataImporter.oldTokyoRow(new String[]{"港区", "G4", "東京タワー", "", "35.65", "139.74",
                "4.5", "90000.0", "tourist_attraction"});
        assertThat(ExpansionDataImporter.famousUnmatched(List.of(ame, small, meiji, matched), Set.of("G4"), 1000))
                .extracting(ExpansionDataImporter.OldTokyoRow::placeId).containsExactly("G1");
        assertThat(ame.reviewCnt()).isEqualTo(12260);
        assertThat(small.address()).isNull();
        assertThat(ExpansionDataImporter.oldTokyoArgs(ame)).hasSize(placeholders(ExpansionDataImporter.OLD_TOKYO_SQL));
    }

    @Test
    void sourceAndCityCodes() {
        assertThat(ExpansionDataImporter.source("kaggle")).isEqualTo(RestaurantSource.KAGGLE);
        assertThat(ExpansionDataImporter.source("apify")).isEqualTo(RestaurantSource.TABELOG);
        assertThatThrownBy(() -> ExpansionDataImporter.source("hotpepper")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ExpansionDataImporter.city("seoul")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void joinsAndCut() {
        assertThat(ExpansionDataImporter.joinGenres(List.of())).isNull();
        assertThat(ExpansionDataImporter.joinDays(List.of("TUE", "WED"))).isEqualTo("TUE,WED");
        assertThat(ExpansionDataImporter.joinDays(null)).isNull();
        assertThat(ExpansionDataImporter.cut("abcdef", 3)).isEqualTo("abc");
        assertThat(ExpansionDataImporter.cut(null, 3)).isNull();
    }

    private static int placeholders(String sql) {
        return (int) sql.chars().filter(c -> c == '?').count();
    }
}
