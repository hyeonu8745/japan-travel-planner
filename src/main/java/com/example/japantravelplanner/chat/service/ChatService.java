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
import com.example.japantravelplanner.chat.edit.ItineraryChatEditService;
import com.example.japantravelplanner.chat.entity.ChatMessage;
import com.example.japantravelplanner.chat.entity.ChatRole;
import com.example.japantravelplanner.chat.repository.ChatMessageRepository;
import com.example.japantravelplanner.global.exception.AiServiceException;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.global.exception.TooManyRequestsException;
import com.example.japantravelplanner.trip.entity.*;
import com.example.japantravelplanner.trip.entity.PreferenceConstraint;
import com.example.japantravelplanner.trip.repository.TripPreferenceRepository;
import com.example.japantravelplanner.trip.repository.TripRepository;
import com.example.japantravelplanner.trip.service.TripAccess;
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

/**
 * 대화 한 번의 흐름 (취향 대화 / 채팅 수정 공통 입구).
 * ① 확인 (트랜잭션): 내 여행인지, 대화할 수 있는 상태인지, 시간당 메시지 수
 * ② AI 호출 (트랜잭션 밖): 일정 전이면 취향 정리, 일정 뒤면 ItineraryChatEditService 로 넘긴다
 * ③ 저장 (트랜잭션): 사용자 말과 AI 답, 정리된 조건, 여행 상태(준비되면 READY_TO_GENERATE)
 */
@Slf4j
@Service
public class ChatService {

    /** 서버가 생성 준비로 넘길 때 붙이는 말 (AI가 덧붙인 질문은 답하지 않아도 된다고 알려준다) */
    static final String READY_BY_SERVER = "위 질문은 답하지 않으셔도 괜찮아요. 지금까지 말씀해 주신 내용으로 일정을 만들 수 있어요. 이제 일정을 만들어드릴까요?";


    private static final int HOURLY_MESSAGE_LIMIT = 30;
    private static final int MAX_CONSTRAINTS = 30;
    private static final List<TripStatus> CHAT_ALLOWED_STATUSES = List.of(
            TripStatus.CONDITIONS_SET, TripStatus.PREFERENCE_GATHERING, TripStatus.READY_TO_GENERATE);
    /** 일정이 만들어진 뒤에는 같은 채팅이 일정 수정 대화가 된다 */
    private static final List<TripStatus> EDIT_STATUSES = List.of(
            TripStatus.AWAITING_CONFIRMATION, TripStatus.GENERATED, TripStatus.EDITING);

    private final TripRepository tripRepository;
    private final TripPreferenceRepository tripPreferenceRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final OpenAiClient openAiClient;
    private final JsonMapper jsonMapper;
    private final ItineraryChatEditService editChat;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    /** AI 호출 앞뒤로 트랜잭션을 나누므로 읽기·쓰기 TransactionTemplate 을 만든다 */
    public ChatService(TripRepository tripRepository,
                       TripPreferenceRepository tripPreferenceRepository,
                       ChatMessageRepository chatMessageRepository,
                       OpenAiClient openAiClient,
                       JsonMapper jsonMapper,
                       ItineraryChatEditService editChat,
                       PlatformTransactionManager transactionManager) {
        this.tripRepository = tripRepository;
        this.tripPreferenceRepository = tripPreferenceRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.openAiClient = openAiClient;
        this.jsonMapper = jsonMapper;
        this.editChat = editChat;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    // ===================== 메시지 전송 =====================

    /** 메시지 하나 처리: 일정이 있으면 채팅 수정으로 넘기고, 없으면 취향 대화(AI) 후 저장 */
    public ChatReplyResponseDto sendMessage(Long userId, Long tripId, String content) {
        // 내 여행인지, 호출 제한을 넘지 않았는지 (두 대화 모두)
        TripStatus tripStatus = readTransaction.execute(s -> checkTrip(userId, tripId));
        if (EDIT_STATUSES.contains(tripStatus)) {
            return editChat.handle(userId, tripId, content);
        }

        // ① 읽기: AI에게 보낼 맥락을 만든다
        String context = readTransaction.execute(s -> buildContext(userId, tripId));

        // ② AI 호출: 수 초가 걸리므로 트랜잭션 밖에서 (DB 연결을 붙잡지 않음)
        String input = context + "\n[사용자 새 메시지]\n<<<\n" + content + "\n>>>";
        AiTurn turn = askAi(tripId, input);

        // ③ 저장: 성공한 경우에만 사용자·AI 메시지와 정리본을 함께 저장
        return writeTransaction.execute(status -> saveTurn(tripId, content, turn));
    }

    /** 내 여행인지와 시간당 메시지 수(30개)를 확인하고 여행 상태를 돌려준다 */
    private TripStatus checkTrip(Long userId, Long tripId) {
        Trip trip = TripAccess.owned(tripRepository, userId, tripId);
        long recentCount = chatMessageRepository.countByTripUserIdAndRoleAndCreatedAtAfter(
                userId, ChatRole.USER, LocalDateTime.now().minusHours(1));
        if (recentCount >= HOURLY_MESSAGE_LIMIT) {
            throw new TooManyRequestsException("메시지를 너무 많이 보냈습니다. 잠시 후 다시 시도해주세요.");
        }
        return trip.getStatus();
    }

    /** AI 에 보낼 맥락: 여행 조건, 지금까지 정리된 취향, 최근 대화 10개 */
    private String buildContext(Long userId, Long tripId) {
        Trip trip = TripAccess.owned(tripRepository, userId, tripId);

        if (!CHAT_ALLOWED_STATUSES.contains(trip.getStatus())) {
            // checkTrip과 이 사이에 일정이 만들어진 경우
            throw new ConflictException("일정이 방금 만들어졌어요. 화면을 새로고침한 뒤 다시 말씀해 주세요.");
        }

        TripPreference preference = tripPreferenceRepository.findByTripId(tripId).orElse(null);
        List<ChatMessage> recent = new ArrayList<>(chatMessageRepository.findTop10ByTripIdOrderByIdDesc(tripId));
        Collections.reverse(recent);   // 최신순으로 가져왔으니 시간순으로 뒤집기

        return describeTrip(trip) + "\n" + describePreference(preference) + "\n" + describeHistory(recent, trip);
    }

    /** 기본 모델로 묻고, 형식 오류면 한 번 더, 그래도 안 되거나 복잡한 요청이면 고급 모델로 */
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

    /** AI 한 번 호출. 응답 형식이 틀리거나 답이 비면 null */
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

    /** 사용자 말·AI 답·정리본 저장, 인원 언급 반영, 준비 판단(ReadyCheck)과 상태 변경, 답이 질문으로 끝나게 */
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

        // ② AI가 준비를 미루며 같은 질문을 되풀이해도, 충분히 들었으면 서버가 준비로 넘긴다 (ReadyCheck)
        List<PreferenceConstraint> constraints = toConstraints(result.constraints());
        long userTurns = chatMessageRepository.countByTripIdAndRole(tripId, ChatRole.USER) + 1;
        boolean ready = result.readyToGenerate() || ReadyCheck.enough(userTurns, constraints);
        boolean forced = ready && !result.readyToGenerate();

        // ③ 준비 전에는 답이 질문으로 끝나게 (AI 가 "잘 전달되었습니다!"처럼 대화를 끊지 않게)
        reply = ReplyFlow.keepGoing(reply, ready, result.userWantsGeneration());

        // ④ 생성 준비 상태로 처음 바뀌는 순간에는 제안 문장을 서버가 보장
        boolean becameReady = ready && trip.getStatus() != TripStatus.READY_TO_GENERATE;
        if (becameReady && !result.userWantsGeneration() && !reply.contains("만들어드릴까요")) {
            if (forced) {
                reply = reply + "\n\n" + READY_BY_SERVER;
            } else {
                reply = reply.contains("?")
                        ? reply + " 특별히 없으시다면 이제 일정을 만들어드릴까요?"
                        : reply + "\n\n이제 일정을 만들어드릴까요?";
            }
        }

        // 화면에서 대화 기록을 열지 않고 바로 보낸 경우에도 첫 인사가 대화 맨 앞에 오게
        if (!chatMessageRepository.existsByTripId(tripId)) {
            saveGreeting(trip);
        }
        ChatMessage userMessage = chatMessageRepository.save(ChatMessage.builder()
                .trip(trip).role(ChatRole.USER).content(content).build());
        ChatMessage assistantMessage = chatMessageRepository.save(ChatMessage.builder()
                .trip(trip).role(ChatRole.ASSISTANT).content(reply).model(turn.model()).build());

        TripPreference preference = tripPreferenceRepository.findByTripId(tripId)
                .orElseGet(() -> tripPreferenceRepository.save(new TripPreference(trip)));
        preference.update(
                constraints,
                parseTime(result.arrivalTime()),
                parseTime(result.departureTime()),
                clampRatio(result.foodBudgetRatio(), preference.getFoodBudgetRatio()),
                result.summary());

        trip.setStatus(ready ? TripStatus.READY_TO_GENERATE : TripStatus.PREFERENCE_GATHERING);

        return new ChatReplyResponseDto(
                ChatMessageResponseDto.from(userMessage),
                ChatMessageResponseDto.from(assistantMessage),
                ready,
                result.userWantsGeneration(),
                trip.getStatus());
    }

    // ===================== 조회 =====================

    /**
     * 대화 기록. 아직 대화가 없고 취향 대화를 할 수 있는 상태면 서버가 첫 인사(AI 호출 없음)를 남기고 돌려준다.
     * 그래서 화면은 대화를 열자마자 AI가 먼저 묻는 모습이 된다.
     */
    public List<ChatMessageResponseDto> getHistory(Long userId, Long tripId) {
        List<ChatMessageResponseDto> history = readTransaction.execute(status -> {
            TripAccess.owned(tripRepository, userId, tripId);
            return chatMessageRepository.findByTripIdOrderByIdAsc(tripId).stream()
                    .map(ChatMessageResponseDto::from)
                    .toList();
        });
        if (!history.isEmpty()) return history;

        return writeTransaction.execute(status -> {
            Trip trip = TripAccess.owned(tripRepository, userId, tripId);
            // 동시에 두 번 열린 경우를 대비해 트랜잭션 안에서 다시 확인
            if (CHAT_ALLOWED_STATUSES.contains(trip.getStatus()) && !chatMessageRepository.existsByTripId(tripId)) {
                saveGreeting(trip);
            }
            return chatMessageRepository.findByTripIdOrderByIdAsc(tripId).stream()
                    .map(ChatMessageResponseDto::from)
                    .toList();
        });
    }

    /** 서버가 만드는 첫 인사 메시지 저장 */
    private ChatMessage saveGreeting(Trip trip) {
        return chatMessageRepository.save(ChatMessage.builder()
                .trip(trip).role(ChatRole.ASSISTANT).content(ChatGreeting.of(trip)).build());
    }

    /** 정리된 취향 (없으면 빈 정리본) */
    public PreferenceResponseDto getPreference(Long userId, Long tripId) {
        return readTransaction.execute(status -> {
            TripAccess.owned(tripRepository, userId, tripId);
            return tripPreferenceRepository.findByTripId(tripId)
                    .map(PreferenceResponseDto::from)
                    .orElseGet(PreferenceResponseDto::empty);
        });
    }

    // ===================== AI 입력 만들기 =====================

    /** AI 입력용 여행 조건 글 */
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
                - 비행기: %s
                """.formatted(
                trip.getStartDate(), trip.getEndDate(), nights, nights + 1,
                trip.getTravelers(),
                trip.getBudget(),
                trip.getTravelPace(), trip.getTravelPace().attractionsPerDay(),
                interests,
                trip.getTransportMode(),
                trip.getAccommodationName(), trip.getAccommodationAddress(),
                blankToNone(trip.getAllergies()),
                blankToNone(trip.getMobilityConstraints()),
                describeFlights(trip));
    }

    /** 여행 조건에 비행기 시각이 있으면 AI가 도착·출발 시각을 다시 묻지 않게 알려준다 */
    static String describeFlights(Trip trip) {
        String arrival = trip.arrivalAirportInfo()
                .map(a -> "도착 " + a.label() + " " + trip.getArrivalFlightTime()).orElse(null);
        String departure = trip.departureAirportInfo()
                .map(a -> "출발 " + a.label() + " " + trip.getDepartureFlightTime()
                        + " (공항에는 " + a.beforeDepartureMinutes() / 60 + "시간 전 도착으로 계산)").orElse(null);
        if (arrival == null && departure == null) return "입력 안 함";
        return (arrival == null ? "도착 입력 안 함" : arrival) + " / " + (departure == null ? "출발 입력 안 함" : departure);
    }

    /** AI 입력용 지금까지 정리된 취향 글 (식비 비율, 조건 목록, 요약) */
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

    /** AI 입력용 최근 대화 글 (대화가 없으면 첫 인사만) */
    private String describeHistory(List<ChatMessage> messages, Trip trip) {
        if (messages.isEmpty()) {
            // 첫 인사가 아직 저장되기 전이라도 AI는 자기가 먼저 음식을 물었다고 알아야 한다
            return "[최근 대화]\nAI: " + ChatGreeting.of(trip) + "\n";
        }
        return "[최근 대화]\n" + messages.stream()
                .map(m -> (m.getRole() == ChatRole.USER ? "사용자: " : "AI: ") + m.getContent())
                .collect(Collectors.joining("\n")) + "\n";
    }

    // ===================== AI 결과 정리 =====================

    /** AI 가 준 조건 → 저장할 조건 (빈 설명은 버리고 최대 30개) */
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

    /** AI 가 준 카테고리 → enum (모르는 값이면 ETC) */
    private ConstraintCategory toCategory(String value) {
        try {
            return ConstraintCategory.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ConstraintCategory.ETC;
        }
    }

    /** "HH:mm" → 시각 (비었거나 틀리면 null) */
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

    /** 비어 있으면 "없음" */
    private String blankToNone(String value) {
        return value == null || value.isBlank() ? "없음" : value;
    }

    /** 최대 길이까지 자른다 */
    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record AiTurn(PreferenceChatResult result, String model) {
    }
}