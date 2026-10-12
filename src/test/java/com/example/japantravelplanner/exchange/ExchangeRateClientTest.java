package com.example.japantravelplanner.exchange;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 환율 응답 읽기 (10-12). 실제 호출은 하지 않는다 */
class ExchangeRateClientTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 9);

    @Test
    void 수출입은행_엔화는_100엔_단위라_100으로_나눈다() {
        String body = """
                [{"result":1,"cur_unit":"USD","deal_bas_r":"1,398.5"},
                 {"result":1,"cur_unit":"JPY(100)","deal_bas_r":"951.05"}]""";

        assertThat(ExchangeRateClient.parseKoreaexim(mapper, body, DAY)).hasValueSatisfying(v ->
                assertThat(v).isEqualTo(9.5105, org.assertj.core.data.Offset.offset(1e-9)));
    }

    @Test
    void 비영업일이나_오전_11시_전에는_빈_값() {
        assertThat(ExchangeRateClient.parseKoreaexim(mapper, "[]", DAY)).isEmpty();
        assertThat(ExchangeRateClient.parseKoreaexim(mapper, "", DAY)).isEmpty();
        assertThat(ExchangeRateClient.parseKoreaexim(mapper, "null", DAY)).isEmpty();
    }

    @Test
    void 인증키_오류나_한도_초과는_다른_날로_다시_묻지_않게_사용_불가로_알린다() {
        assertThatThrownBy(() -> ExchangeRateClient.parseKoreaexim(mapper, "[{\"result\":3}]", DAY))
                .isInstanceOf(ExchangeRateClient.Unavailable.class);
        assertThatThrownBy(() -> ExchangeRateClient.parseKoreaexim(mapper, "[{\"result\":4}]", DAY))
                .isInstanceOf(ExchangeRateClient.Unavailable.class);
    }

    @Test
    void Frankfurter는_1엔당_원과_기준일을_읽는다() {
        String body = """
                {"amount":1.0,"base":"JPY","date":"2026-10-09","rates":{"KRW":9.47}}""";

        assertThat(ExchangeRateClient.parseFrankfurter(mapper, body))
                .contains(new ExchangeRateClient.Dated(9.47, DAY));
        assertThat(ExchangeRateClient.parseFrankfurter(mapper, "{\"rates\":{}}")).isEmpty();
    }
}
