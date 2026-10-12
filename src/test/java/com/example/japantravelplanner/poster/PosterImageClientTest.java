package com.example.japantravelplanner.poster;

import com.example.japantravelplanner.global.exception.AiServiceException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PosterImageClientTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void 응답의_base64_그림을_바이트로() {
        String b64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        assertThat(PosterImageClient.parse(mapper, "{\"data\":[{\"b64_json\":\"" + b64 + "\"}]}"))
                .containsExactly(1, 2, 3);
    }

    @Test
    void 그림이_없으면_AI_오류() {
        assertThatThrownBy(() -> PosterImageClient.parse(mapper, "{\"data\":[]}")).isInstanceOf(AiServiceException.class);
        assertThatThrownBy(() -> PosterImageClient.parse(mapper, "")).isInstanceOf(AiServiceException.class);
    }
}
