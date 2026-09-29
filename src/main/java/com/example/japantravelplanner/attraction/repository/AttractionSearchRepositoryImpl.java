package com.example.japantravelplanner.attraction.repository;

import com.example.japantravelplanner.attraction.dto.AttractionSearchCondition;
import com.example.japantravelplanner.attraction.entity.Attraction;
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
public class AttractionSearchRepositoryImpl implements AttractionSearchRepository {

    private static final String BASE_POINT =
            "ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography";

    private final EntityManager entityManager;

    @Override
    public Page<Attraction> search(AttractionSearchCondition condition) {
        Map<String, Object> params = new HashMap<>();
        String where = buildWhere(condition, params);
        String orderBy = buildOrderBy(condition);

        String selectSql = "SELECT a.* FROM attractions a" + where + " ORDER BY " + orderBy;
        String countSql = "SELECT COUNT(*) FROM attractions a" + where;

        Query selectQuery = entityManager.createNativeQuery(selectSql, Attraction.class);
        Query countQuery = entityManager.createNativeQuery(countSql);
        params.forEach((name, value) -> {
            selectQuery.setParameter(name, value);
            countQuery.setParameter(name, value);
        });

        int page = condition.getPage();
        int size = condition.getSize();
        selectQuery.setFirstResult(page * size);
        selectQuery.setMaxResults(size);

        @SuppressWarnings("unchecked")
        List<Attraction> content = selectQuery.getResultList();
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }

    private String buildWhere(AttractionSearchCondition c, Map<String, Object> params) {
        List<String> conditions = new ArrayList<>();

        if (c.hasLocation()) {
            conditions.add("ST_DWithin(a.location::geography, " + BASE_POINT + ", :radius)");
            params.put("lat", c.getLat());
            params.put("lng", c.getLng());
            params.put("radius", c.getRadius());
        }

        if (c.getWard() != null && !c.getWard().isBlank()) {
            conditions.add("a.ward = :ward");
            params.put("ward", c.getWard().trim());
        }

        if (c.hasTypes()) {
            // 지정한 type 중 하나라도 관광지의 types 목록에 정확히 있으면 포함 (OR)
            List<String> typeConditions = new ArrayList<>();
            int index = 0;
            for (String type : c.getTypes()) {
                if (type == null || type.isBlank()) {
                    continue;
                }
                String paramName = "type" + index++;   // type0, type1 ... (코드가 만든 이름)
                typeConditions.add(":" + paramName + " = ANY(string_to_array(a.types, ','))");
                params.put(paramName, type.trim());
            }
            if (!typeConditions.isEmpty()) {
                conditions.add("(" + String.join(" OR ", typeConditions) + ")");
            }
        }

        if (c.getMinRating() != null) {
            conditions.add("a.rating >= :minRating");
            params.put("minRating", c.getMinRating());
        }

        if (c.getMinReviews() != null) {
            conditions.add("a.review_cnt >= :minReviews");
            params.put("minReviews", c.getMinReviews());
        }

        if (conditions.isEmpty()) {
            return "";
        }
        return " WHERE " + String.join(" AND ", conditions);
    }

    private String buildOrderBy(AttractionSearchCondition c) {
        String primary = switch (c.getSort()) {
            case DISTANCE -> "ST_Distance(a.location::geography, " + BASE_POINT + ") ASC";
            case RATING -> "a.rating DESC NULLS LAST, a.review_cnt DESC NULLS LAST";
            case REVIEWS -> "a.review_cnt DESC NULLS LAST";
        };
        return primary + ", a.id ASC";
    }
}