package com.example.japantravelplanner.dataimport;

import com.example.japantravelplanner.dataimport.ExpansionRows.AttractionRow;
import com.example.japantravelplanner.dataimport.ExpansionRows.ChainRow;
import com.example.japantravelplanner.dataimport.ExpansionRows.FacilityRow;
import com.example.japantravelplanner.dataimport.ExpansionRows.TabelogRow;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 실제 데이터 파일(data/processed, 저장소에 없음)이 있을 때만: 모든 줄이 읽히고 SQL 값으로 바뀌는지, 건수가 맞는지.
 * 파일이 없는 환경에서는 건너뛴다.
 */
class ExpansionDataFilesTest {

    private static final Path DIR = Path.of("data/processed");
    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Test
    void everyRowConverts() throws Exception {
        assumeTrue(Files.exists(DIR.resolve("clean_attractions.json")), "데이터 파일 없음");

        List<TabelogRow> tabelog = JSON.readValue(Files.readString(DIR.resolve("clean_restaurants_tabelog.json")),
                new TypeReference<>() { });
        List<ChainRow> chains = JSON.readValue(Files.readString(DIR.resolve("clean_restaurants_chain.json")),
                new TypeReference<>() { });
        List<AttractionRow> attractions = JSON.readValue(Files.readString(DIR.resolve("clean_attractions.json")),
                new TypeReference<>() { });
        List<FacilityRow> facilities = JSON.readValue(Files.readString(DIR.resolve("clean_facilities.json")),
                new TypeReference<>() { });

        assertThat(tabelog).hasSize(17_573);
        assertThat(chains).hasSize(3_906);
        assertThat(attractions).hasSize(3_750);
        assertThat(facilities).hasSize(65_595);

        tabelog.forEach(ExpansionDataImporter::tabelogArgs);
        chains.forEach(c -> ExpansionDataImporter.chainArgs(c, 1L));
        attractions.forEach(ExpansionDataImporter::attractionArgs);
        facilities.forEach(ExpansionDataImporter::facilityArgs);

        assertThat(chains.stream().map(ChainRow::brand).distinct().count()).isEqualTo(86);
        Set<String> storeIds = tabelog.stream().map(TabelogRow::storeId).collect(Collectors.toSet());
        assertThat(storeIds).hasSize(tabelog.size());
        // 도쿄 식당은 구 이름이 다 나와야 예전 구 검색이 동작한다
        long tokyoNoWard = tabelog.stream().filter(r -> "tokyo".equals(r.city()))
                .filter(r -> AddressParts.of(r.address()).municipality() == null).count();
        assertThat(tokyoNoWard).isZero();
    }
}
