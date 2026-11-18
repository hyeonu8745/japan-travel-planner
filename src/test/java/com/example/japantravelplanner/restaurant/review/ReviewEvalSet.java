package com.example.japantravelplanner.restaurant.review;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 시험 문장 묶음 (src/test/resources/review/review_eval.tsv) 읽기 */
final class ReviewEvalSet {

    private ReviewEvalSet() {
    }

    /** @param expected 정답 종류 (없으면 빈 집합) */
    record Case(Set<ReviewTag> expected, String sentence) {
    }

    static List<Case> load() {
        List<Case> cases = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                ReviewEvalSet.class.getResourceAsStream("/review/review_eval.tsv")), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split("\t", 2);
                Set<ReviewTag> expected = EnumSet.noneOf(ReviewTag.class);
                if (!parts[0].equals("NONE")) {
                    for (String t : parts[0].split(",")) expected.add(ReviewTag.valueOf(t.trim()));
                }
                cases.add(new Case(expected, parts[1].strip()));
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return cases;
    }
}
