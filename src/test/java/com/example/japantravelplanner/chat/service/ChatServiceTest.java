package com.example.japantravelplanner.chat.service;

import com.example.japantravelplanner.ai.client.AiResult;
import com.example.japantravelplanner.ai.client.OpenAiClient;
import com.example.japantravelplanner.chat.entity.ChatMessage;
import com.example.japantravelplanner.chat.entity.ChatRole;
import com.example.japantravelplanner.chat.repository.ChatMessageRepository;
import com.example.japantravelplanner.trip.entity.Interest;
import com.example.japantravelplanner.trip.entity.TransportMode;
import com.example.japantravelplanner.trip.entity.TravelPace;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.entity.TripStatus;
import com.example.japantravelplanner.trip.repository.TripPreferenceRepository;
import com.example.japantravelplanner.trip.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 취향 대화: 서버의 첫 인사와 대화를 끊지 않는 답 (AI는 가짜) */
class ChatServiceTest {

    private static final long USER_ID = 1L;
    private static final long TRIP_ID = 5L;

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripPreferenceRepository preferenceRepository = mock(TripPreferenceRepository.class);
    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private final OpenAiClient openAiClient = mock(OpenAiClient.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final com.example.japantravelplanner.chat.edit.ItineraryChatEditService editChat =
            mock(com.example.japantravelplanner.chat.edit.ItineraryChatEditService.class);

    private final Trip trip = Trip.builder().id(TRIP_ID)
            .startDate(LocalDate.of(2026, 10, 12)).endDate(LocalDate.of(2026, 10, 18))
            .travelers(1).budget(1_600_000).travelPace(TravelPace.NORMAL)
            .interests(List.of(Interest.FOOD)).transportMode(TransportMode.PUBLIC_TRANSIT)
            .accommodationName("숙소").accommodationAddress("도쿄").status(TripStatus.CONDITIONS_SET).build();

    private ChatService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).thenReturn(Optional.of(trip));
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
        when(chatMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(preferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new ChatService(tripRepository, preferenceRepository, chatMessageRepository, openAiClient,
                JsonMapper.builder().build(), editChat, transactionManager);
    }

    private List<ChatMessage> savedMessages() {
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private static AiResult ai(String reply, boolean ready) {
        return new AiResult("""
                {"reply_to_user":"%s","constraints":[{"category":"ATMOSPHERE","description":"조용한 곳"}],
                 "arrival_time":null,"departure_time":null,"food_budget_ratio":0.4,"summary":"조용한 곳을 좋아함",
                 "ready_to_generate":%s,"user_wants_generation":false,"complexity":"LOW",
                 "complexity_reason":null,"mentioned_travelers":null}""".formatted(reply, ready),
                "gpt-4o-mini", 100, 50, 0, 300);
    }

    // ── 첫 인사 ──

    @Test
    void 대화를_처음_열면_서버가_첫_인사를_남기고_돌려준다() {
        when(chatMessageRepository.findByTripIdOrderByIdAsc(TRIP_ID)).thenReturn(List.of(),
                List.of(ChatMessage.builder().trip(trip).role(ChatRole.ASSISTANT).content("인사").build()));

        assertThat(service.getHistory(USER_ID, TRIP_ID)).hasSize(1);

        List<ChatMessage> saved = savedMessages();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getRole()).isEqualTo(ChatRole.ASSISTANT);
        assertThat(saved.get(0).getContent()).endsWith(ChatGreeting.FIRST_QUESTION);
        verifyNoInteractions(openAiClient);   // 첫 인사는 AI를 부르지 않는다
    }

    @Test
    void 대화가_이미_있거나_일정이_만들어진_여행이면_첫_인사를_남기지_않는다() {
        when(chatMessageRepository.findByTripIdOrderByIdAsc(TRIP_ID)).thenReturn(
                List.of(ChatMessage.builder().trip(trip).role(ChatRole.USER).content("안녕").build()));
        service.getHistory(USER_ID, TRIP_ID);

        trip.setStatus(TripStatus.GENERATED);
        when(chatMessageRepository.findByTripIdOrderByIdAsc(TRIP_ID)).thenReturn(List.of());
        service.getHistory(USER_ID, TRIP_ID);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void 기록을_열지_않고_바로_보내도_첫_인사가_맨_앞에_저장되고_AI도_첫_질문을_안다() {
        when(chatMessageRepository.existsByTripId(TRIP_ID)).thenReturn(false);
        when(openAiClient.generateJson(any(), anyString(), anyString(), anyMap()))
                .thenReturn(ai("조용한 곳 위주로 볼게요. 첫날 숙소에는 몇 시쯤 도착하세요?", false));

        service.sendMessage(USER_ID, TRIP_ID, "조용한 곳이 좋아요");

        List<ChatMessage> saved = savedMessages();
        assertThat(saved).extracting(ChatMessage::getRole)
                .containsExactly(ChatRole.ASSISTANT, ChatRole.USER, ChatRole.ASSISTANT);
        assertThat(saved.get(0).getContent()).endsWith(ChatGreeting.FIRST_QUESTION);

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).generateJson(any(), anyString(), input.capture(), anyMap());
        assertThat(input.getValue()).contains("AI: 안녕하세요! TabiMate예요.");
    }

    // ── 대화를 끊지 않기 ──

    @Test
    void 준비_전에_대화를_끝내는_답이면_다음_질문을_붙여_저장한다() {
        when(chatMessageRepository.existsByTripId(TRIP_ID)).thenReturn(true);
        when(openAiClient.generateJson(any(), anyString(), anyString(), anyMap()))
                .thenReturn(ai("조용한 분위기를 선호하시는군요. 취향이 잘 전달되었습니다!", false));

        var response = service.sendMessage(USER_ID, TRIP_ID, "조용한 곳이 좋아요");

        assertThat(response.assistantMessage().content()).endsWith(ReplyFlow.FOLLOW_UP);
        assertThat(savedMessages()).extracting(ChatMessage::getRole).containsExactly(ChatRole.USER, ChatRole.ASSISTANT);
    }

    @Test
    void 일정이_만들어진_여행의_메시지는_일정_수정_대화로_넘긴다() {
        // 10-11: 예전에는 409로 막았다
        trip.setStatus(TripStatus.EDITING);

        service.sendMessage(USER_ID, TRIP_ID, "2일차 저녁을 스시로 바꿔줘");

        verify(editChat).handle(USER_ID, TRIP_ID, "2일차 저녁을 스시로 바꿔줘");
        verifyNoInteractions(openAiClient);
    }

    @Test
    void 완성_전_확인_대기_여행도_일정_수정_대화로_넘긴다() {
        // 10-12: 확인 대기 중에도 고칠 수 있다 (사용자 결정)
        trip.setStatus(TripStatus.AWAITING_CONFIRMATION);

        service.sendMessage(USER_ID, TRIP_ID, "닌자 카페 빼줘");

        verify(editChat).handle(USER_ID, TRIP_ID, "닌자 카페 빼줘");
        verifyNoInteractions(openAiClient);
    }
}
