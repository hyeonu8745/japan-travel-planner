package com.example.japantravelplanner.restaurant.review;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AI가 놓친 경험을 다시 찾기 위한 "의심 문장" 고르기 (11-18).
 *
 * 1차 AI 분석은 잘못 붙인 표시가 없게(정밀도) 서버가 다시 확인하지만, AI가 처음부터 못 찾은 경험은 살릴 수 없었다
 * (시험 문장 재현율: 차별 0.6). 그래서 서버의 종류별 규칙({@link ReviewTag#supportedBy})으로 리뷰를 문장마다 훑어서,
 * 규칙에 걸리는데 1차에서 그 리뷰에 그 종류가 붙지 않은 문장만 골라 AI에게 한 번 더 묻는다 (ReviewRecheckPrompt).
 * 단어 규칙만으로 정하지 않는다: "외국인 손님이 많아서 무시할 수 없는 맛집"처럼 말만 맞고 뜻이 다른 문장이 많다.
 */
final class ReviewSuspects {

    /** 한 번에 다시 물을 문장 수 (상세 화면이 느려지지 않게) */
    static final int MAX_SUSPECTS = 8;
    /** 이보다 긴 문장은 앞부분만 (문장 나누기가 안 된 긴 리뷰) */
    static final int MAX_SENTENCE_LENGTH = 200;

    /**
     * 문장 끝: 마침표·느낌표·물음표(일본어 포함)와 그 뒤 공백, 줄바꿈, 이모티콘 끝(ㅠㅠ·^^·~~ 뒤 공백).
     * 띄어쓰기만으로는 나누지 않는다 ("직원이 바쁘다 보니"가 갈라지면 직원 말과 태도 말이 떨어진다)
     */
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?。！？]+\\s*|\\n+|(?<=[ㅠㅜ^~]{2})\\s+");

    /** 상수와 정적 메서드만 쓴다 */
    private ReviewSuspects() {
    }

    /**
     * @param index    리뷰 번호 (google.reviews 위치)
     * @param tag      규칙에 걸린 종류
     * @param sentence 그 문장 (리뷰 글 그대로)
     */
    record Suspect(int index, ReviewTag tag, String sentence) {
    }

    /** 리뷰 글을 문장으로 나눈다 (빈 문장은 뺀다) */
    static List<String> sentences(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;
        for (String s : SENTENCE_END.split(text.strip())) {
            String t = s.strip();
            if (t.isEmpty()) continue;
            result.add(t.length() > MAX_SENTENCE_LENGTH ? t.substring(0, MAX_SENTENCE_LENGTH) : t);
        }
        return result;
    }

    /**
     * 규칙에 걸리는데 1차 결과에 없는 (리뷰, 종류) 문장들. 한 리뷰·한 종류는 첫 문장 하나만, 모두 합쳐 MAX_SUSPECTS 개까지
     *
     * @param texts 분석 대상 리뷰 글 (한국인이 쓴 것만, 나머지는 빈 글)
     * @param found 1차에서 남은 "리뷰번호:종류" (예: "2:UNFRIENDLY")
     */
    static List<Suspect> scan(List<String> texts, Set<String> found) {
        List<Suspect> result = new ArrayList<>();
        for (int i = 0; i < texts.size() && result.size() < MAX_SUSPECTS; i++) {
            List<String> sentences = sentences(texts.get(i));
            for (ReviewTag tag : ReviewTag.values()) {
                if (found.contains(i + ":" + tag)) continue;
                for (String sentence : sentences) {
                    if (tag.supportedBy(sentence)) {
                        result.add(new Suspect(i, tag, sentence));
                        break;
                    }
                }
                if (result.size() >= MAX_SUSPECTS) break;
            }
        }
        return result;
    }
}
