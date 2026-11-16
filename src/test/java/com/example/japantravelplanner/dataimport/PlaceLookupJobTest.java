package com.example.japantravelplanner.dataimport;

import com.example.japantravelplanner.global.google.GooglePlacesClient;
import com.example.japantravelplanner.global.google.GooglePlacesClient.LatLng;
import com.example.japantravelplanner.global.google.GooglePlacesClient.LocalizedText;
import com.example.japantravelplanner.global.google.GooglePlacesClient.SearchPlace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceLookupJobTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final GooglePlacesClient places = mock(GooglePlacesClient.class);
    private final PlaceLookupProperties props = new PlaceLookupProperties();
    private PlaceLookupJob job;

    @BeforeEach
    void setUp() {
        props.setDelayMillis(0);
        DataImportProperties importProps = new DataImportProperties();
        importProps.setDataDir("no-such-dir");
        job = new PlaceLookupJob(jdbc, places, props, importProps);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString(), anyLong())).thenReturn(0);
    }

    @SuppressWarnings("unchecked")
    private void targets(PlaceLookupJob.Target... ts) {
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt())).thenReturn(List.of(ts));
    }

    private static SearchPlace google(String id, String name, double lat, double lng, Double rating, Integer count) {
        return new SearchPlace(id, new LocalizedText(name, "ja"), new LatLng(lat, lng), "OPERATIONAL", rating, count);
    }

    @Test
    void ratingTargetsUseEnterpriseOthersUsePro() {
        targets(new PlaceLookupJob.Target(1, "清水寺", 34.9949, 135.7850, true),
                new PlaceLookupJob.Target(2, "六波羅蜜寺", 34.9966, 135.7735, false));
        when(places.searchTextWithRating(eq("清水寺"), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(google("G1", "清水寺", 34.9950, 135.7850, 4.5, 60000)));
        when(places.searchText(eq("六波羅蜜寺"), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(google("G2", "六波羅蜜寺", 34.9966, 135.7736, null, null)));

        PlaceLookupJob.Result r = job.run();

        assertThat(r.enterpriseCalls()).isEqualTo(1);
        assertThat(r.proCalls()).isEqualTo(1);
        assertThat(r.matched()).isEqualTo(2);
        assertThat(r.ratings()).isEqualTo(1);
        verify(jdbc).update(anyString(), eq("G1"), eq("MATCHED"), any(), eq(4.5), eq(60000), eq(1L));
        verify(jdbc).update(anyString(), eq("G2"), eq("MATCHED"), any(), isNull(), isNull(), eq(2L));
    }

    @Test
    void differentPlaceIsNotSaved() {
        targets(new PlaceLookupJob.Target(3, "清水寺", 34.9949, 135.7850, false));
        when(places.searchText(anyString(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(google("G9", "京都タワー", 34.9875, 135.7593, null, null)));

        PlaceLookupJob.Result r = job.run();

        assertThat(r.notFound()).isEqualTo(1);
        verify(jdbc).update(anyString(), isNull(), eq("NOT_FOUND"), any(), isNull(), isNull(), eq(3L));
    }

    @Test
    void placeIdUsedByAnotherAttractionIsNotSaved() {
        targets(new PlaceLookupJob.Target(4, "上野恩賜公園", 35.7146, 139.7732, false));
        when(places.searchText(anyString(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(google("G5", "上野恩賜公園", 35.7150, 139.7730, null, null)));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("G5"), eq(4L))).thenReturn(1);

        PlaceLookupJob.Result r = job.run();

        assertThat(r.duplicates()).isEqualTo(1);
        verify(jdbc).update(anyString(), isNull(), eq("NOT_FOUND"), any(), isNull(), isNull(), eq(4L));
    }

    @Test
    void stopsAtProLimitAndFallsBackFromEnterprise() {
        props.setMaxProCalls(1);
        props.setMaxEnterpriseCalls(0);
        targets(new PlaceLookupJob.Target(5, "金閣寺", 35.0394, 135.7292, true),
                new PlaceLookupJob.Target(6, "銀閣寺", 35.0270, 135.7982, false));
        when(places.searchText(anyString(), anyDouble(), anyDouble(), anyDouble(), anyInt())).thenReturn(List.of());

        PlaceLookupJob.Result r = job.run();

        assertThat(r.stoppedAtLimit()).isTrue();
        assertThat(r.checked()).isEqualTo(1);
        verify(places, never()).searchTextWithRating(anyString(), anyDouble(), anyDouble(), anyDouble(), anyInt());
        verify(places, times(1)).searchText(anyString(), anyDouble(), anyDouble(), anyDouble(), anyInt());
    }
}
