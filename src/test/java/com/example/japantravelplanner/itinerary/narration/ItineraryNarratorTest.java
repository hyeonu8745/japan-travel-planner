package com.example.japantravelplanner.itinerary.narration;

import com.example.japantravelplanner.ai.client.AiModelTier;
import com.example.japantravelplanner.ai.client.AiResult;
import com.example.japantravelplanner.ai.client.OpenAiClient;
import com.example.japantravelplanner.ai.config.OpenAiProperties;
import com.example.japantravelplanner.global.exception.AiServiceException;
import com.example.japantravelplanner.itinerary.planner.ScheduledDay;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.trip.entity.TravelPace;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static com.example.japantravelplanner.itinerary.narration.NarrationTestData.*;
import static org.assertj.core.api.Assertions.assertThat;

class ItineraryNarratorTest {

    /** 실제 OpenAI 대신 정해진 답을 돌려주는 가짜 클라이언트 */
    private static class FakeClient extends OpenAiClient {
        String json;
        RuntimeException error;
        int calls;
        AiModelTier lastTier;
        String lastInput;
        int lastMaxTokens;

        FakeClient() {
            super(new OpenAiProperties());
        }

        @Override
        public AiResult generateJson(AiModelTier tier, String instructions, String input,
                                     Map<String, Object> format, int maxOutputTokens) {
            calls++;
            lastTier = tier;
            lastInput = input;
            lastMaxTokens = maxOutputTokens;
            if (error != null) throw error;
            return new AiResult(json, "fake", 100, 100, 0, 1);
        }
    }

    private static final TripBrief BRIEF = new TripBrief(2, TravelPace.NORMAL, List.of(), List.of());

    private final FakeClient client = new FakeClient();
    private final ItineraryNarrator narrator = new ItineraryNarrator(client, JsonMapper.builder().build());

    private final ScheduledDay day1 = day(1, List.of(
            attraction("新宿東口の猫", "tourist_attraction", "16:15"),
            meal(StopType.DINNER, "つけ麺 五ノ神製作所", "つけ麺", "18:59")), List.of());
    private final ScheduledDay day2 = day(2, List.of(
            attraction("六本木ヒルズ展望台", "observation_deck", "10:07")), List.of());

    private Map<Integer, String> run(ScheduledDay... days) {
        return narrator.narrate(BRIEF, List.of(days), types -> List.of());
    }

    @Test
    void 날짜별_설명을_돌려주고_Terra로_한_번만_호출한다() {
        client.json = """
                {"days":[{"day":1,"description":"신주쿠에서 시작해요. 츠케멘으로 저녁을 먹어요."},
                         {"day":2,"description":"롯폰기에서 도시 전망을 즐겨요."}]}""";

        Map<Integer, String> result = run(day1, day2);

        assertThat(result).containsEntry(1, "신주쿠에서 시작해요. 츠케멘으로 저녁을 먹어요.")
                .containsEntry(2, "롯폰기에서 도시 전망을 즐겨요.");
        assertThat(client.calls).isEqualTo(1);
        assertThat(client.lastTier).isEqualTo(AiModelTier.ADVANCED);
        assertThat(client.lastInput).contains("新宿東口の猫").contains("六本木ヒルズ展望台");
    }

    @Test
    void 출력_한도는_날수에_비례한다() {
        client.json = "{\"days\":[]}";
        run(day1, day2);
        assertThat(client.lastMaxTokens)
                .isEqualTo(ItineraryNarrator.BASE_OUTPUT_TOKENS + 2 * ItineraryNarrator.TOKENS_PER_DAY);
    }

    @Test
    void 일정이_없는_날은_요청하지_않고_모두_비면_호출도_안_한다() {
        ScheduledDay empty = day(3, List.of(), List.of());

        assertThat(run(empty)).isEmpty();
        assertThat(client.calls).isZero();

        client.json = "{\"days\":[{\"day\":1,\"description\":\"신주쿠의 하루예요.\"}]}";
        run(day1, empty);
        assertThat(client.lastInput).doesNotContain("DAY 3");
    }

    @Test
    void 없는_날짜_번호와_중복은_버린다() {
        client.json = """
                {"days":[{"day":1,"description":"첫 번째 설명이에요."},
                         {"day":1,"description":"중복 설명이에요."},
                         {"day":7,"description":"없는 날짜예요."}]}""";

        Map<Integer, String> result = run(day1);

        assertThat(result).containsOnlyKeys(1);
        assertThat(result.get(1)).isEqualTo("첫 번째 설명이에요.");
    }

    @Test
    void 규칙을_어긴_설명만_빠지고_나머지는_남는다() {
        client.json = """
                {"days":[{"day":1,"description":"입장료 없이 즐길 수 있는 하루예요."},
                         {"day":2,"description":"전망대에서 도시를 내려다봐요."}]}""";

        Map<Integer, String> result = run(day1, day2);

        assertThat(result).containsOnlyKeys(2);
    }

    @Test
    void AI가_실패해도_예외_없이_빈_결과() {
        client.error = new AiServiceException("실패", null);
        assertThat(run(day1)).isEmpty();
    }

    @Test
    void 형식이_잘못된_응답도_빈_결과() {
        client.json = "이건 JSON이 아니에요";
        assertThat(run(day1)).isEmpty();
    }

    @Test
    void 날짜_목록이_비어_있어도_빈_결과() {
        client.json = "{\"days\":null}";
        assertThat(run(day1)).isEmpty();
    }
}