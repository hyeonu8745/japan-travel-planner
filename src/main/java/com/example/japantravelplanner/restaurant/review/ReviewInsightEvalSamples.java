package com.example.japantravelplanner.restaurant.review;

import java.util.List;
import java.util.Set;

import static com.example.japantravelplanner.restaurant.review.ReviewTag.*;

/**
 * 리뷰 분석 정확도 확인용 시험 문장 (개발용, 10-10).
 * 실제 리뷰에서는 차별 경험이 드물어서, 종류마다 정답을 단 문장으로 맞힌 비율·잘못 붙인 비율을 잰다.
 * 헷갈리기 쉬운 "아님" 문장(다른 대우지만 이유 없음, 배탈, 대기, 일반 칭찬, 국적 단어만 있음)을 일부러 넣었다.
 * 10-10 실제 식당 20곳 표본에서 잘못 붙었던 유형과 놓친 유형도 넣었다. 리뷰 글은 저장하면 안 되므로(Google 약관)
 * 실제 문장을 옮기지 않고 같은 유형으로 새로 썼다.
 *
 * @param expected 반드시 찾아야 하는 종류
 * @param allowed  찾아도 틀린 것으로 보지 않는 종류 (예: 차별 문장의 불친절)
 */
record ReviewInsightEvalSamples(String text, Set<ReviewTag> expected, Set<ReviewTag> allowed) {

    static ReviewInsightEvalSamples of(String text, Set<ReviewTag> expected) {
        return new ReviewInsightEvalSamples(text, expected, Set.of());
    }

    static final List<ReviewInsightEvalSamples> ALL = List.of(
            // ── 차별·혐한 ──
            new ReviewInsightEvalSamples("한국어로 주문하니까 갑자기 표정이 굳더니 구석 자리로 안내했어요. 일본인 손님들은 창가로 앉히던데요.",
                    Set.of(DISCRIMINATION), Set.of(UNFRIENDLY)),
            new ReviewInsightEvalSamples("외국인이라고 메뉴판도 안 주고 계속 무시당했어요.", Set.of(DISCRIMINATION), Set.of(UNFRIENDLY)),
            new ReviewInsightEvalSamples("한국 사람인 걸 알자마자 직원이 퉁명스럽게 대하더라고요.", Set.of(DISCRIMINATION), Set.of(UNFRIENDLY)),
            of("관광객은 안 받는다며 자리가 비어 있는데도 돌려보냈어요.", Set.of(DISCRIMINATION)),
            of("외국인 손님에게만 자릿세를 따로 받았어요.", Set.of(DISCRIMINATION)),

            // ── 불친절 ──
            of("직원이 주문 받을 때 눈도 안 마주치고 툭 던지듯 말했어요.", Set.of(UNFRIENDLY)),
            of("사장님이 너무 무뚝뚝하고 질문하니까 귀찮다는 듯이 대답했어요.", Set.of(UNFRIENDLY)),
            of("점원이 물을 쾅 내려놓고 가서 기분이 상했어요.", Set.of(UNFRIENDLY)),
            of("알바생 응대가 최악이었어요.", Set.of(UNFRIENDLY)),
            of("종업원 태도가 불친절해서 다시 안 갈 것 같아요.", Set.of(UNFRIENDLY)),

            // ── 위생 ──
            of("테이블이 끈적끈적하고 바닥에 휴지가 굴러다녀서 지저분했어요.", Set.of(HYGIENE)),
            of("라멘에서 머리카락이 나왔어요.", Set.of(HYGIENE)),
            of("화장실이 너무 더러웠어요.", Set.of(HYGIENE)),
            of("주방 쪽에 바퀴벌레가 지나가는 걸 봤어요.", Set.of(HYGIENE)),

            // ── 한국인에게 좋은 점 ──
            of("한국어 메뉴판이 있어서 주문하기 쉬웠어요.", Set.of(KOREAN_FRIENDLY)),
            of("한국어 하는 직원분이 계셔서 편했어요.", Set.of(KOREAN_FRIENDLY)),
            of("혼밥하기 좋은 1인석이 있어요.", Set.of(KOREAN_FRIENDLY)),
            of("한국인 손님이라고 하니 한글 안내문을 따로 주셨어요.", Set.of(KOREAN_FRIENDLY)),
            of("혼자 가기 부담 없는 분위기예요.", Set.of(KOREAN_FRIENDLY)),

            // ── 섞인 경험 ──
            of("점원 한 명은 불친절했지만 나머지 직원은 친절했어요.", Set.of(UNFRIENDLY)),
            of("한국어 메뉴는 있었지만 직원이 퉁명스러웠어요.", Set.of(UNFRIENDLY, KOREAN_FRIENDLY)),

            // ── 아무것도 아님 (헷갈리기 쉬운 문장) ──
            of("다른 손님은 넓은 자리에 앉혔는데 저는 좁은 자리였어요.", Set.of()),
            of("느끼해서 먹고 나서 배탈이 났어요.", Set.of()),
            of("1시간 기다릴 맛은 아니에요.", Set.of()),
            of("가격이 너무 비싸요.", Set.of()),
            of("직원분들이 정말 친절했어요.", Set.of()),
            of("일본인 남자친구도 맛있다고 했어요.", Set.of()),
            of("한국인들이 많이 찾는 맛집이라 와봤어요.", Set.of()),
            of("자리가 좁고 시끄러워요.", Set.of()),
            of("주문하고 음식이 나오기까지 30분이나 걸렸어요.", Set.of()),
            of("외국인 관광객이 많아서 줄이 길었어요.", Set.of()),
            of("맛은 평범했고 양이 적었어요.", Set.of()),
            of("혼자 왔는데 카운터 자리로 안내받았어요.", Set.of()),
            of("인스타 감성 인테리어가 예뻐요.", Set.of()),

            // ── 10-10 실제 표본에서 잘못 붙었던 유형 (아무것도 아님) ──
            of("한국인 입맛에도 딱 맞는 우동이에요.", Set.of()),
            of("한국인이라면 누구나 좋아할 맛이에요.", Set.of()),
            of("한국인이 정말 많이 오는 규카츠집이에요.", Set.of()),
            of("외국인 손님들이 줄을 서 있어서 30분 기다렸어요.", Set.of()),
            of("직원분들도 정말 친절하시고 음식도 빨리 나왔어요.", Set.of()),
            of("직원이 특별히 친절하진 않았지만 불친절하다는 느낌은 아니었어요.", Set.of()),
            of("일본어를 못하면 주문 안내가 조금 서툴 수 있어요.", Set.of()),
            of("직원에게 물어본 것 말고는 전부 셀프예요.", Set.of()),

            // ── 10-10 실제 표본에 있던 유형 (찾아야 함) ──
            of("키오스크에 한글 메뉴가 있어요.", Set.of(KOREAN_FRIENDLY)),
            of("한국분이 일하고 계셔서 설명을 잘 들었어요.", Set.of(KOREAN_FRIENDLY)),
            of("가게 안에서 하수구 냄새가 났어요.", Set.of(HYGIENE)),
            of("위생은 좀 걱정스러웠어요.", Set.of(HYGIENE))
    );
}
