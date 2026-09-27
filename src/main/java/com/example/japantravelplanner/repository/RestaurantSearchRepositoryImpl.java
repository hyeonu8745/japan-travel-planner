package com.example.japantravelplanner.repository;

import com.example.japantravelplanner.dto.RestaurantSearchCondition;
import com.example.japantravelplanner.entity.Restaurant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class RestaurantSearchRepositoryImpl implements RestaurantSearchRepository {

    // 검색 기준점 (lat, lng 파라미터로 만든 geography 포인트)
    private static final String BASE_POINT =
            "ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography";

    private final EntityManager entityManager;

    @Override
    public Page<Restaurant> search(RestaurantSearchCondition condition) {
        Map<String, Object> params = new HashMap<>();
        String where = buildWhere(condition, params);
        String orderBy = buildOrderBy(condition);

        // 목록 쿼리와 count 쿼리가 같은 WHERE절을 공유한다
        String selectSql = "SELECT r.* FROM restaurants r" + where + " ORDER BY " + orderBy;
        String countSql = "SELECT COUNT(*) FROM restaurants r" + where;

        Query selectQuery = entityManager.createNativeQuery(selectSql, Restaurant.class);
        Query countQuery = entityManager.createNativeQuery(countSql);
        params.forEach((name, value) -> {
            selectQuery.setParameter(name, value);
            countQuery.setParameter(name, value);
        });

        int page = condition.getPage();
        int size = condition.getSize();
        selectQuery.setFirstResult(page * size);   // OFFSET
        selectQuery.setMaxResults(size);           // LIMIT

        @SuppressWarnings("unchecked")
        List<Restaurant> content = selectQuery.getResultList();
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }

    /** 값이 들어온 조건만 WHERE절에 추가하고, 바인딩할 파라미터를 params에 담는다. */
    private String buildWhere(RestaurantSearchCondition c, Map<String, Object> params) {
        List<String> conditions = new ArrayList<>();

        if (c.hasLocation()) {
            conditions.add("ST_DWithin(r.location::geography, " + BASE_POINT + ", :radius)");
            params.put("lat", c.getLat());
            params.put("lng", c.getLng());
            params.put("radius", c.getRadius());
        }

        if (c.getGenre() != null && !c.getGenre().isBlank()) {
            conditions.add("r.genre LIKE :genre");
            params.put("genre", "%" + c.getGenre().trim() + "%");
        }

        if (c.getMinRating() != null) {
            conditions.add("r.rating_val >= :minRating");
            params.put("minRating", c.getMinRating());
        }

        if (c.getMaxBudget() != null) {
            // 컬럼명은 사용자 입력이 아니라 enum으로만 결정된다 (SQL 인젝션 불가)
            String budgetColumn = switch (c.getMealType()) {
                case LUNCH -> "r.budget_lunch_max";
                case DINNER -> "r.budget_dinner_max";
            };
            // 엄격 기준: 상한(max)이 예산 이하. max가 NULL("-", "￥100,000～")이면 자동 제외
            conditions.add(budgetColumn + " <= :maxBudget");
            params.put("maxBudget", c.getMaxBudget());
        }

        if (conditions.isEmpty()) {
            return "";
        }
        return " WHERE " + String.join(" AND ", conditions);
    }

    /** 정렬 기준. 마지막에 r.id를 붙여 페이지 간 순서가 항상 같도록 고정한다. */
    private String buildOrderBy(RestaurantSearchCondition c) {
        String primary = switch (c.getSort()) {
            case DISTANCE -> "ST_Distance(r.location::geography, " + BASE_POINT + ") ASC";
            case RATING -> "r.rating_val DESC NULLS LAST, r.review_cnt DESC NULLS LAST";
            case REVIEWS -> "r.review_cnt DESC NULLS LAST";
        };
        return primary + ", r.id ASC";
    }
}