package com.example.japantravelplanner.itinerary.meal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 비슷한 음식끼리 묶은 표. 여행 중 같은 묶음이 반복되면 점수를 깎는 데 쓴다.
 * 타베로그는 ラーメン과 つけ麺을 다른 장르로 적어서, 장르 이름만 비교하면 라멘 → 츠케멘이 "다른 음식"으로 통과한다.
 * 묶음을 늘리려면 아래 표에 한 줄 추가하면 된다.
 */
public final class GenreGroups {

    private static final Map<String, List<String>> GROUPS = Map.of(
            "RAMEN", List.of("ラーメン", "つけ麺", "油そば", "まぜそば", "担々麺")
    );

    private GenreGroups() {
    }

    /** 장르 목록이 속한 묶음 (장르 이름에 키워드가 들어 있으면 해당) */
    public static Set<String> groupsOf(Set<String> genres) {
        Set<String> result = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> group : GROUPS.entrySet()) {
            boolean matched = genres.stream()
                    .anyMatch(genre -> group.getValue().stream().anyMatch(genre::contains));
            if (matched) result.add(group.getKey());
        }
        return result;
    }
}