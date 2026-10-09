package com.example.japantravelplanner.restaurant.match;

import com.example.japantravelplanner.global.exception.ExternalApiException;
import com.example.japantravelplanner.global.google.GooglePlacesClient;
import com.example.japantravelplanner.global.google.GooglePlacesClient.LatLng;
import com.example.japantravelplanner.global.google.GooglePlacesClient.LocalizedText;
import com.example.japantravelplanner.global.google.GooglePlacesClient.SearchPlace;
import com.example.japantravelplanner.restaurant.entity.GoogleMatchStatus;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 고른 식당을 Google에서 확인하고 결과를 저장한다 (저장소·Google은 가짜) */
class RestaurantMatchServiceTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private final RestaurantRepository repository = mock(RestaurantRepository.class);
    private final GooglePlacesClient placesClient = mock(GooglePlacesClient.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final RestaurantMatchProperties props = new RestaurantMatchProperties();

    // 트랜잭션이 열려 있는 동안 true (Google 호출이 트랜잭션 밖인지 확인)
    private final boolean[] transactionActive = {false};

    private final Restaurant sushi = restaurant(1, "鮨いしかわ");
    private final Restaurant naruse = restaurant(2, "レストラン鳴海");

    private RestaurantMatchService service;

    private static Restaurant restaurant(long id, String name) {
        return Restaurant.builder().id(id).name(name).address("東京都千代田区")
                .location(GF.createPoint(new Coordinate(139.70, 35.70))).build();
    }

    private static SearchPlace place(String id, String name) {
        return new SearchPlace(id, new LocalizedText(name, "ja"), new LatLng(35.70, 139.70), "OPERATIONAL");
    }

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(inv -> {
            transactionActive[0] = true;
            return mock(TransactionStatus.class);
        });
        doAnswer(inv -> {
            transactionActive[0] = false;
            return null;
        }).when(transactionManager).commit(any());

        when(repository.findAllById(anyCollection())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return List.of(sushi, naruse).stream().filter(r -> ids.contains(r.getId())).toList();
        });
        when(placesClient.searchText(contains("鮨いしかわ"), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(place("p-sushi", "鮨いしかわ")));
        when(placesClient.searchText(contains("レストラン鳴海"), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(place("p-other", "ベンガル家庭料理 トルカリ")));

        service = new RestaurantMatchService(repository, placesClient, props, transactionManager);
    }

    @Test
    void 찾은_식당은_place_ID를_찾지_못한_식당은_결과만_저장하고_빠진_수를_돌려준다() {
        int excluded = service.verify(Set.of(1L, 2L));

        assertThat(excluded).isEqualTo(1);
        assertThat(sushi.getGoogleMatchStatus()).isEqualTo(GoogleMatchStatus.MATCHED);
        assertThat(sushi.getGooglePlaceId()).isEqualTo("p-sushi");
        assertThat(sushi.getGoogleMatchedAt()).isNotNull();
        assertThat(naruse.getGoogleMatchStatus()).isEqualTo(GoogleMatchStatus.NOT_FOUND);
        assertThat(naruse.getGooglePlaceId()).isNull();
        assertThat(naruse.isExcludedByGoogleMatch()).isTrue();
    }

    @Test
    void 검색어는_이름과_주소이고_설정한_반경과_결과_수로_찾는다() {
        service.verify(Set.of(1L));

        verify(placesClient).searchText(eq("鮨いしかわ 東京都千代田区"), eq(35.70), eq(139.70),
                eq(props.getSearchRadiusMeters()), eq(props.getPageSize()));
    }

    @Test
    void 이미_확인한_식당은_다시_검색하지_않는다() {
        sushi.recordGoogleMatch(GoogleMatchStatus.MATCHED, "p-sushi", LocalDateTime.now());

        int excluded = service.verify(Set.of(1L));

        assertThat(excluded).isZero();
        verifyNoInteractions(placesClient);
    }

    @Test
    void Google이_실패한_식당은_기록하지_않고_통과시킨다() {
        when(placesClient.searchText(contains("鮨いしかわ"), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new ExternalApiException("장소를 검색하지 못했어요.", new RuntimeException()));

        int excluded = service.verify(Set.of(1L, 2L));

        assertThat(sushi.getGoogleMatchStatus()).isNull();   // 다음에 다시 확인
        assertThat(naruse.getGoogleMatchStatus()).isEqualTo(GoogleMatchStatus.NOT_FOUND);   // 다른 식당은 그대로 진행
        assertThat(excluded).isEqualTo(1);
    }

    @Test
    void Google_검색은_트랜잭션_밖에서_한다() {
        boolean[] calledInTransaction = {false};
        when(placesClient.searchText(anyString(), anyDouble(), anyDouble(), anyDouble(), anyInt())).thenAnswer(inv -> {
            calledInTransaction[0] |= transactionActive[0];
            return List.of();
        });

        service.verify(Set.of(1L, 2L));

        assertThat(calledInTransaction[0]).isFalse();
    }

    @Test
    void 꺼져_있으면_확인하지_않고_다시_계산도_하지_않는다() {
        props.setEnabled(false);

        assertThat(service.verify(Set.of(1L, 2L))).isZero();
        assertThat(service.verifyRounds()).isZero();
        verifyNoInteractions(placesClient, repository);
    }

    @Test
    void 확인할_식당이_없으면_아무것도_하지_않는다() {
        assertThat(service.verify(Set.of())).isZero();
        verifyNoInteractions(placesClient, repository);
    }
}
