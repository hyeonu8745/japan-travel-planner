package com.example.japantravelplanner.chat.service;

import com.example.japantravelplanner.ai.client.AiModelTier;
import com.example.japantravelplanner.ai.client.AiResult;
import com.example.japantravelplanner.ai.client.OpenAiClient;
import com.example.japantravelplanner.chat.ai.PreferenceChatResult;
import com.example.japantravelplanner.chat.ai.PreferenceChatSchema;
import com.example.japantravelplanner.chat.ai.PreferencePrompt;
import com.example.japantravelplanner.chat.dto.ChatMessageResponseDto;
import com.example.japantravelplanner.chat.dto.ChatReplyResponseDto;
import com.example.japantravelplanner.chat.dto.PreferenceResponseDto;
import com.example.japantravelplanner.chat.entity.ChatMessage;
import com.example.japantravelplanner.chat.entity.ChatRole;
import com.example.japantravelplanner.chat.repository.ChatMessageRepository;
import com.example.japantravelplanner.global.exception.AiServiceException;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.global.exception.TooManyRequestsException;
import com.example.japantravelplanner.trip.entity.*;
import com.example.japantravelplanner.trip.repository.TripPreferenceRepository;
import com.example.japantravelplanner.trip.repository.TripRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatService {

    private static final int HOURLY_MESSAGE_LIMIT = 30;
    private static final int MAX_CONSTRAINTS = 30;
    private static final List<TripStatus> CHAT_ALLOWED_STATUSES = List.of(
            TripStatus.CONDITIONS_SET, TripStatus.PREFERENCE_GATHERING, TripStatus.READY_TO_GENERATE);

    private final TripRepository tripRepository;
    private final TripPreferenceRepository tripPreferenceRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final OpenAiClient openAiClient;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public ChatService(TripRepository tripRepository,
                       TripPreferenceRepository tripPreferenceRepository,
                       ChatMessageRepository chatMessageRepository,
                       OpenAiClient openAiClient,
                       JsonMapper jsonMapper,
                       PlatformTransactionManager transactionManager) {
        this.tripRepository = tripRepository;
        this.tripPreferenceRepository = tripPreferenceRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.openAiClient = openAiClient;
        this.jsonMapper = jsonMapper;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    // ===================== 메시지 전송 =====================

    public ChatReplyResponseDto sendMessage(Long userId, Long tripId, String content) {
        // ① 읽기: 검증하고 AI에게 보낼 맥락을 만든다
        String context = readTransaction.execute(status -> buildContext(userId, tripId));

        // ② AI 호출: 수 초가 걸리므로 트랜잭션 밖에서 (DB 연결을 붙잡지 않음)
        String input = context + "\n[사용자 새 메시지]\n<<<\n" + content + "\n>>>";
        AiTurn turn = askAi(tripId, input);

        // ③ 저장: 성공한 경우에만 사용자·AI 메시지와 정리본을 함께 저장
        return writeTransaction.execute(status -> saveTurn(tripId, content, turn));
    }

    private String buildContext(Long userId, Long tripId) {
        Trip trip = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new NotFoundException("여행을 찾을 수 없습니다."));

        if (!CHAT_ALLOWED_STATUSES.contains(trip.getStatus())) {
            throw new ConflictException("이미 일정이 만들어진 여행입니다. 일정 수정은 플래너에서 진행해주세요.");
        }

        long recentCount = chatMessageRepository.countByTripUserIdAndRoleAndCreatedAtAfter(
                userId, ChatRole.USER, LocalDateTime.now().minusHours(1));
        if (recentCount >= HOURLY_MESSAGE_LIMIT) {
            throw new TooManyRequestsException("메시지를 너무 많이 보냈습니다. 잠시 후 다시 시도해주세요.");
        }

        TripPreference preference = tripPreferenceRepository.findByTripId(tripId).orElse(null);
        List<ChatMessage> recent = new ArrayList<>(chatMessageRepository.findTop10ByTripIdOrderByIdDesc(tripId));
        Collections.reverse(recent);   // 최신순으로 가져왔으니 시간순으로 뒤집기

        return describeTrip(trip) + "\n" + describePreference(preference) + "\n" + describeHistory(recent);
    }

    private AiTurn askAi(Long tripId, String input) {
        AiTurn turn = tryAsk(AiModelTier.BASIC, input);
        if (turn == null) {
            turn = tryAsk(AiModelTier.BASIC, input);   // 형식 오류면 한 번 더
        }
        if (turn == null || turn.result().isComplex()) {
            log.info("[Chat] ADVANCED 전환 tripId={} reason={}", tripId,
                    turn == null ? "응답 형식 오류" : turn.result().complexityReason());
            turn = tryAsk(AiModelTier.ADVANCED, input);
        }
        if (turn == null) {
            throw new AiServiceException("AI 응답을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.", null);
        }
        return turn;
    }

    private AiTurn tryAsk(AiModelTier tier, String input) {
        AiResult ai = openAiClient.generateJson(tier, PreferencePrompt.INSTRUCTIONS, input, PreferenceChatSchema.format());
        try {
            PreferenceChatResult result = jsonMapper.readValue(ai.text(), PreferenceChatResult.class);
            if (result.replyToUser() == null || result.replyToUser().isBlank()) {
                return null;
            }
            return new AiTurn(result, ai.model());
        } catch (JacksonException e) {
            log.warn("[Chat] 응답 해석 실패 model={} message={}", ai.model(), e.getOriginalMessage());
            return null;
        }
    }

    private ChatReplyResponseDto saveTurn(Long tripId, String content, AiTurn turn) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new NotFoundException("여행을 찾을 수 없습니다."));
        PreferenceChatResult result = turn.result();
        String reply = result.replyToUser();

        // ① 대화에서 말한 인원이 여행 조건과 다르면, 서버가 확인 안내를 붙임
        Integer mentioned = result.mentionedTravelers();
        if (mentioned != null && mentioned > 0 && !mentioned.equals(trip.getTravelers())) {
            reply = reply + "\n\n참고로 여행 조건에는 " + trip.getTravelers() + "명으로 입력되어 있어요. "
                    + "인원이 바뀌었다면 플래너의 '여행 조건' 메뉴에서 수정해주세요.";
        }

        // ② 생성 준비 상태로 처음 바뀌는 순간에는 제안 문장을 서버가 보장
        boolean becameReady = result.readyToGenerate() && trip.getStatus() != TripStatus.READY_TO_GENERATE;
        if (becameReady && !result.userWantsGeneration() && !reply.contains("만들어드릴까요")) {
            reply = reply.contains("?")
                    ? reply + " 특별히 없으시다면 이제 일정을 만들어드릴까요?"
                    : reply + "\n\n이제 일정을 만들어드릴까요?";
        }

        ChatMessage userMessage = chatMessageRepository.save(ChatMessage.builder()
                .trip(trip).role(ChatRole.USER).content(content).build());
        ChatMessage assistantMessage = chatMessageRepository.save(ChatMessage.builder()
                .trip(trip).role(ChatRole.ASSISTANT).content(reply).model(turn.model()).build());

        TripPreference preference = tripPreferenceRepository.findByTripId(tripId)
                .orElseGet(() -> tripPreferenceRepository.save(new TripPreference(trip)));
        preference.update(
                toConstraints(result.constraints()),
                parseTime(result.arrivalTime()),
                parseTime(result.departureTime()),
                clampRatio(result.foodBudgetRatio(), preference.getFoodBudgetRatio()),
                result.summary());

        trip.setStatus(result.readyToGenerate() ? TripStatus.READY_TO_GENERATE : TripStatus.PREFERENCE_GATHERING);

        return new ChatReplyResponseDto(
                ChatMessageResponseDto.from(userMessage),
                ChatMessageResponseDto.from(assistantMessage),
                result.readyToGenerate(),
                result.userWantsGeneration(),
                trip.getStatus());
    }

    // ===================== 조회 =====================

    public List<ChatMessageResponseDto> getHistory(Long userId, Long tripId) {
        return readTransaction.execute(status -> {
            tripRepository.findByIdAndUserId(tripId, userId)
                    .orElseThrow(() -> new NotFoundException("여행을 찾을 수 없습니다."));
            return chatMessageRepository.findByTripIdOrderByIdAsc(tripId).stream()
                    .map(ChatMessageResponseDto::from)
                    .toList();
        });
    }

    public PreferenceResponseDto getPreference(Long userId, Long tripId) {
        return readTransaction.execute(status -> {
            tripRepository.findByIdAndUserId(tripId, userId)
                    .orElseThrow(() -> new NotFoundException("여행을 찾을 수 없습니다."));
            return tripPreferenceRepository.findByTripId(tripId)
                    .map(PreferenceResponseDto::from)
                    .orElseGet(PreferenceResponseDto::empty);
        });
    }

    // ===================== AI 입력 만들기 =====================

    private String describeTrip(Trip trip) {
        long nights = ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate());
        String interests = trip.getInterests() == null || trip.getInterests().isEmpty()
                ? "없음"
                : trip.getInterests().stream().map(Enum::name).collect(Collectors.joining(", "));
        return """
                [여행 조건]
                - 기간: %s ~ %s (%d박 %d일)
                - 인원: %d명
                - 전체 예산: %,d원
                - 여행 속도: %s (하루 관광지 %d곳)
                - 관심사: %s
                - 이동수단: %s
                - 숙소: %s (%s)
                - 알레르기·제외 식품: %s
                - 이동 제약: %s
                """.formatted(
                trip.getStartDate(), trip.getEndDate(), nights, nights + 1,
                trip.getTravelers(),
                trip.getBudget(),
                trip.getTravelPace(), trip.getTravelPace().attractionsPerDay(),
                interests,
                trip.getTransportMode(),
                trip.getAccommodationName(), trip.getAccommodationAddress(),
                blankToNone(trip.getAllergies()),
                blankToNone(trip.getMobilityConstraints()));
    }

    private String describePreference(TripPreference preference) {
        if (preference == null) {
            return "[현재까지 정리된 취향]\n- 아직 없음 (첫 대화)\n- 식비 비율: 0.4\n";
        }
        String constraints = preference.getConstraints().isEmpty()
                ? "없음"
                : preference.getConstraints().stream()
                  .map(c -> c.getCategory() + ": " + c.getDescription())
                  .collect(Collectors.joining(" / "));
        return """
                [현재까지 정리된 취향]
                - 조건: %s
                - 첫날 도착 시각: %s
                - 마지막 날 출발 시각: %s
                - 식비 비율: %s
                - 요약: %s
                """.formatted(
                constraints,
                preference.getArrivalTime() == null ? "모름" : preference.getArrivalTime(),
                preference.getDepartureTime() == null ? "모름" : preference.getDepartureTime(),
                preference.getFoodBudgetRatio(),
                blankToNone(preference.getSummary()));
    }

    private String describeHistory(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return "[최근 대화]\n- 없음\n";
        }
        return "[최근 대화]\n" + messages.stream()
                .map(m -> (m.getRole() == ChatRole.USER ? "사용자: " : "AI: ") + m.getContent())
                .collect(Collectors.joining("\n")) + "\n";
    }

    // ===================== AI 결과 정리 =====================

    private List<PreferenceConstraint> toConstraints(List<PreferenceChatResult.ConstraintItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .filter(item -> item.description() != null && !item.description().isBlank())
                .limit(MAX_CONSTRAINTS)
                .map(item -> new PreferenceConstraint(toCategory(item.category()),
                        truncate(item.description().trim(), 300)))
                .toList();
    }

    private ConstraintCategory toCategory(String value) {
        try {
            return ConstraintCategory.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ConstraintCategory.ETC;
        }
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** AI가 엉뚱한 값을 줘도 20~70% 범위 안으로. 값이 없으면 기존 값 유지 */
    private Double clampRatio(Double value, Double current) {
        if (value == null) {
            return current;
        }
        return Math.max(0.2, Math.min(0.7, value));
    }

    private String blankToNone(String value) {
        return value == null || value.isBlank() ? "없음" : value;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record AiTurn(PreferenceChatResult result, String model) {
    }
}