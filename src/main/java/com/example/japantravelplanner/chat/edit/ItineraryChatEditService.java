package com.example.japantravelplanner.chat.edit;

import com.example.japantravelplanner.ai.client.AiModelTier;
import com.example.japantravelplanner.ai.client.AiResult;
import com.example.japantravelplanner.ai.client.OpenAiClient;
import com.example.japantravelplanner.chat.dto.ChatMessageResponseDto;
import com.example.japantravelplanner.chat.dto.ChatReplyResponseDto;
import com.example.japantravelplanner.chat.entity.ChatMessage;
import com.example.japantravelplanner.chat.entity.ChatRole;
import com.example.japantravelplanner.chat.repository.ChatMessageRepository;
import com.example.japantravelplanner.global.exception.AiServiceException;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.exception.NotFoundException;
import com.example.japantravelplanner.itinerary.dictionary.FoodDictionary;
import com.example.japantravelplanner.itinerary.dictionary.FoodPreferenceType;
import com.example.japantravelplanner.itinerary.dictionary.PlaceTypeLabels;
import com.example.japantravelplanner.itinerary.dto.AttractionCandidatesResponseDto;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.dto.StopCandidatesResponseDto;
import com.example.japantravelplanner.itinerary.entity.Itinerary;
import com.example.japantravelplanner.itinerary.entity.ItineraryDay;
import com.example.japantravelplanner.itinerary.entity.ItineraryStop;
import com.example.japantravelplanner.itinerary.naming.GenreLabels;
import com.example.japantravelplanner.itinerary.naming.KoreanNameService;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.repository.ItineraryRepository;
import com.example.japantravelplanner.itinerary.service.ItineraryAttractionEditService;
import com.example.japantravelplanner.itinerary.service.ItineraryEditService;
import com.example.japantravelplanner.itinerary.service.ItineraryStopEditService;
import com.example.japantravelplanner.trip.entity.Trip;
import com.example.japantravelplanner.trip.repository.TripRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 일정을 만든 뒤 채팅으로 고치기 (10-11, 이슈 #28 "수정 요청").
 *
 *  ① 사용자 말 → 명령: AI(gpt-4o-mini)는 말을 정해진 형식(EditCommand: 무엇을, 어느 항목, 무엇으로)으로 바꾸기만 한다.
 *     "네", "1번"처럼 뻔한 답은 AI를 부르지 않고 서버가 알아듣는다
 *  ② 명령 → 제안: 서버가 기존 후보 API(식당·관광지 교체 후보, 관광지 추가 후보)에서 맞는 곳을 찾아
 *     "이렇게 바꿀까요?"(하나) 또는 "어느 곳으로 할까요?"(여럿)라고 묻고, 답을 기다리는 변경을 메시지에 저장한다 (PendingEdit)
 *  ③ 동의·선택 → 적용: 기존 교체·삭제·추가·순서 변경 서비스가 새 버전을 만든다 (버튼으로 고칠 때와 같은 검증·잠금)
 *
 * 묻는 사이에 일정 버전이 바뀌면 적용하지 않는다 (항목 id가 버전마다 다르다).
 * 애매하면 짐작하지 않고 되묻는다. AI가 실패해도 오류 대신 예시와 함께 다시 말해 달라고 답한다.
 */
@Slf4j
@Service
public class ItineraryChatEditService {

    static final int MAX_OPTIONS = 3;
    static final int RECENT_MESSAGES = 4;
    static final String HELP = "예를 들어 \"2일차 저녁을 스시로 바꿔줘\", \"3일차 두 번째 장소 빼줘\", "
            + "\"1일차에 정원 하나 넣어줘\"처럼 말씀해 주세요.";

    static final String POSTER_GUIDE = "포스터는 채팅으로는 만들 수 없어요. 포스터 화면의 [만들기]에서 그림 스타일(수채화·레트로·미니멀·스티커)과 "
            + "넣고 싶은 캐릭터 설명을 골라 만들 수 있어요. 일정을 바꾼 뒤라면 거기서 다시 만들어 주세요.";
    private static final Pattern POSTER = Pattern.compile("포스터|poster", Pattern.CASE_INSENSITIVE);

    /** 포스터를 만들거나 다시 그려 달라는 말 */
    static boolean isPosterRequest(String content) {
        return content != null && POSTER.matcher(content).find();
    }

    private static final Pattern REF = Pattern.compile("D(\\d+)-(\\d+)");
    private static final Pattern YES = Pattern.compile(
            "^(네|넵|예|응|어|좋아요?|좋습니다|그래요?|그렇게\\s*(해|하자|해\\s*줘|해\\s*주세요)|바꿔\\s*(줘|주세요)?|ㅇㅇ|ok|오케이)[\\s.!~]*$");
    /**
     * "어 거기에 넣어줘", "응 그걸로 바꿔줘", "그렇게 해 주세요"처럼 대기 중인 변경을 하라는 말.
     * 10-11: "어 거기에 넣어줘"를 AI가 같은 추가 요청으로 다시 읽어서 같은 질문을 되풀이했다
     */
    private static final Pattern DO_IT = Pattern.compile(
            "^(네|넵|예|응|어|그래|좋아|좋아요|오케이|ok)?[\\s,.!~]*((거기|그곳|그거|그걸|이거|이걸|그쪽)(로|으로|에|에다)?\\s*)?"
                    + "(그렇게\\s*)?(넣어|바꿔|빼|옮겨|해)\\s*(줘|주세요|줄래|줄래요|요|봐|봐요)?[\\s.!~]*$");
    private static final Pattern NO = Pattern.compile(
            "^(아니(요|오)?|아뇨|취소|됐어(요)?|괜찮아(요)?|그대로(\\s*둬(요|주세요)?)?|하지\\s*마(세요)?)[\\s.!~]*$");
    /** "2번", "1번으로 바꿔줘", "3번 할게요" (10-11 여행 9: "1번으로 바꿔줘"를 못 알아들어 AI가 다른 요청으로 읽었다) */
    private static final Pattern NUMBER = Pattern.compile("^\\s*([1-9])\\s*(번|번째)?\\s*(으로|로|요|이요)?\\s*"
            + "((바꿔|해|넣어|골라|할게|갈게|부탁해)\\s*(줘|주세요|줄래|줄래요|요)?)?[\\s.!~]*$");
    private static final List<String> ORDINALS = List.of("첫", "두", "세");

    private final TripRepository tripRepository;
    private final ItineraryRepository itineraryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final OpenAiClient openAiClient;
    private final JsonMapper jsonMapper;
    private final ItineraryEditService editService;
    private final ItineraryAttractionEditService attractionEditService;
    private final ItineraryStopEditService stopEditService;
    private final FoodDictionary foodDictionary;
    private final PlaceTypeLabels typeLabels;
    private final KoreanNameService koreanNames;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public ItineraryChatEditService(TripRepository tripRepository, ItineraryRepository itineraryRepository,
                                    ChatMessageRepository chatMessageRepository, OpenAiClient openAiClient,
                                    JsonMapper jsonMapper, ItineraryEditService editService,
                                    ItineraryAttractionEditService attractionEditService,
                                    ItineraryStopEditService stopEditService, FoodDictionary foodDictionary,
                                    PlaceTypeLabels typeLabels, KoreanNameService koreanNames,
                                    PlatformTransactionManager transactionManager) {
        this.tripRepository = tripRepository;
        this.itineraryRepository = itineraryRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.openAiClient = openAiClient;
        this.jsonMapper = jsonMapper;
        this.editService = editService;
        this.attractionEditService = attractionEditService;
        this.stopEditService = stopEditService;
        this.foodDictionary = foodDictionary;
        this.typeLabels = typeLabels;
        this.koreanNames = koreanNames;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    /** 일정이 있는 여행의 채팅 메시지 하나를 처리한다. 여행 확인·호출 제한은 ChatService가 먼저 했다 */
    public ChatReplyResponseDto handle(Long userId, Long tripId, String content) {
        Snapshot snapshot = readTransaction.execute(status -> snapshot(tripId));
        // 10-16: 포스터는 채팅으로 만들지 않고 포스터 화면으로 안내한다 (AI 없이, 사용자 결정 10-14: 채팅 만들기는 나중에)
        if (snapshot.pending() == null && isPosterRequest(content)) {
            return writeTransaction.execute(status -> save(tripId, content, Outcome.reply(POSTER_GUIDE)));
        }
        EditCommand command = quickCommand(content, snapshot.pending());
        if (command == null) command = askAi(snapshot, content);

        Outcome outcome = execute(userId, tripId, snapshot, command);
        return writeTransaction.execute(status -> save(tripId, content, outcome));
    }

    // ===================== ① 말 → 명령 =====================

    /** 대기 중인 변경이 있을 때 "네", "아니요", "2번"은 AI 없이 알아듣는다 */
    static EditCommand quickCommand(String content, PendingEdit pending) {
        if (pending == null || content == null) return null;
        String text = content.strip().toLowerCase(Locale.ROOT);
        if (NO.matcher(text).matches()) return EditCommand.of("CANCEL");
        boolean hasOptions = pending.options() != null && !pending.options().isEmpty();
        if (hasOptions) {
            // 선택지가 하나여도 "1번으로"는 고르기다 (하나뿐인 제안에 "1번으로 바꿔줘"라고 답한 적이 있다)
            Matcher number = NUMBER.matcher(text);
            if (number.matches()) return EditCommand.choose(Integer.parseInt(number.group(1)));
            for (int i = 0; i < ORDINALS.size(); i++) {
                if (text.matches("^" + ORDINALS.get(i) + "\\s*번째.{0,8}$")) return EditCommand.choose(i + 1);
            }
            if (pending.needsChoice()) return null;
        }
        return YES.matcher(text).matches() || DO_IT.matcher(text).matches() ? EditCommand.of("CONFIRM") : null;
    }

    private EditCommand askAi(Snapshot snapshot, String content) {
        // 사용자 글 속 구분자는 지워서 구분자 밖으로 빠져나가지 못하게 한다
        String safe = content.replace("<<<", "").replace(">>>", "");
        String input = snapshot.text() + "\n[사용자 새 메시지]\n<<<\n" + safe + "\n>>>";
        try {
            AiResult ai = openAiClient.generateJson(AiModelTier.BASIC, EditPrompt.INSTRUCTIONS, input,
                    EditPrompt.format(), 400, 0.0);
            EditCommand command = jsonMapper.readValue(ai.text(), EditCommand.class);
            log.info("[ChatEdit] 명령 {} ref={} after={} day={} target={}", command.intent(), command.stopRef(),
                    command.afterRef(), command.day(), command.target());
            return command;
        } catch (AiServiceException | JacksonException e) {
            log.warn("[ChatEdit] 명령으로 바꾸지 못함: {}", e.getMessage());
            return null;
        }
    }

    // ===================== ② 명령 → 제안 / ③ 적용 =====================

    Outcome execute(Long userId, Long tripId, Snapshot snapshot, EditCommand command) {
        if (command == null) {
            return Outcome.reply("말씀을 잘 이해하지 못했어요. " + HELP);
        }
        if (notBlank(command.clarifyQuestion())) {
            return Outcome.reply(command.clarifyQuestion().strip());
        }
        PendingEdit pending = snapshot.pending();
        String intent = command.intent() == null ? "OTHER" : command.intent();
        // 방금 보여준 선택지 이름을 말하면 그곳으로 (10-11 여행 9: "오히츠젠 탄보로 바꿔줘"를 AI가 새 교체 요청으로 읽어
        // 그 이름으로 다시 찾다가 "맞는 곳이 없어요"라고 했다)
        Integer named = ("REPLACE".equals(intent) || "ADD".equals(intent) || "CHOOSE".equals(intent))
                && samePlaceAsPending(snapshot, pending, command) ? optionNamed(pending, command.target()) : null;
        if (named != null) return choose(userId, tripId, snapshot, pending, named);
        return switch (intent) {
            case "CONFIRM" -> confirm(userId, tripId, snapshot, pending);
            case "CHOOSE" -> choose(userId, tripId, snapshot, pending, command.choiceIndex());
            case "CANCEL" -> Outcome.reply(pending == null ? "알겠어요." : "알겠어요, 그대로 둘게요.");
            case "REPLACE" -> proposeReplace(userId, tripId, snapshot, command);
            case "DELETE" -> proposeDelete(snapshot, command);
            case "MOVE" -> proposeMove(snapshot, command);
            case "ADD" -> proposeAdd(userId, tripId, snapshot, command);
            default -> Outcome.reply(notBlank(command.reply()) ? command.reply().strip() : "일정 수정은 " + HELP);
        };
    }

    private Outcome confirm(Long userId, Long tripId, Snapshot snapshot, PendingEdit pending) {
        if (pending == null) return Outcome.reply("지금 확인할 변경이 없어요. " + HELP);
        if (pending.needsChoice()) return Outcome.reply("번호로 골라 주세요.", pending);
        PendingEdit.Option only = pending.options() == null || pending.options().isEmpty() ? null : pending.options().get(0);
        return apply(userId, tripId, snapshot, pending, only);
    }

    private Outcome choose(Long userId, Long tripId, Snapshot snapshot, PendingEdit pending, Integer index) {
        if (pending == null || pending.options() == null || pending.options().isEmpty()) {
            return Outcome.reply("지금 고를 선택지가 없어요. " + HELP);
        }
        if (index == null || index < 1 || index > pending.options().size()) {
            return Outcome.reply("1부터 " + pending.options().size() + " 사이의 번호로 골라 주세요.", pending);
        }
        return apply(userId, tripId, snapshot, pending, pending.options().get(index - 1));
    }

    private Outcome proposeReplace(Long userId, Long tripId, Snapshot snapshot, EditCommand command) {
        StopRef stop = snapshot.refs().get(normalizeRef(command.stopRef()));
        if (stop == null) return Outcome.reply("어느 항목을 바꿀지 찾지 못했어요. 몇 일차의 어떤 장소인지 알려주세요.");
        String target = blankToNull(command.target());
        String where = "DAY" + stop.day() + " " + stop.kindLabel();

        List<Scored> options = new ArrayList<>();
        String kind;
        if (stop.type() == StopType.ATTRACTION) {
            kind = PendingEdit.REPLACE_ATTRACTION;
            AttractionCandidatesResponseDto found = attractionEditService.candidates(userId, tripId, stop.stopId());
            for (var c : found.candidates()) {
                options.add(new Scored(c.id(), false, c.name(), c.nameKo(),
                        attractionDetail(c.labels(), c.travel(), c.fitsInDay()),
                        matchesPlace(target, c.name(), c.nameKo(), c.labels(), true), false));
            }
        } else {
            kind = PendingEdit.REPLACE_MEAL;
            Set<String> genres = target == null ? Set.of() : foodDictionary.findGenres(target, FoodPreferenceType.PREFER);
            // 원하는 음식이 있으면 그 장르만 넓게(3km, 예산 조금 넘는 곳까지) 먼저 찾는다.
            // 10-11 여행 8: "둘째날 스시" → 보통 후보(500m, 예산 안)에는 스시집이 없어 이탈리안·소바만 나왔다
            StopCandidatesResponseDto found = genres.isEmpty() ? null
                    : editService.candidates(userId, tripId, stop.stopId(), genres);
            if (found == null || found.candidates().isEmpty()) {
                found = editService.candidates(userId, tripId, stop.stopId());
            }
            for (var c : found.candidates()) {
                // 식당은 사용자 말 안에 장르 이름이 들어 있는 것("장어덮밥" 안의 "덮밥")은 맞는 것으로 보지 않는다 (10-11)
                boolean match = target != null && (!Collections.disjoint(genresOf(c.genre()), genres)
                        || matchesPlace(target, c.name(), c.nameKo(), c.genreLabels(), false));
                options.add(new Scored(c.id(), true, c.name(), c.nameKo(),
                        mealDetail(c.genre(), c.rating(), c.travel(), c.budgetRelaxed(), c.closedOnVisitDay()),
                        match, c.closedOnVisitDay()));
            }
            // 방문하는 날이 정기 휴일인 곳은 뒤로 (빼지는 않는다. 문구로 알려준다).
            // 10-11 여행 9: 채팅으로 고른 1일차 점심 天三이 그날 정기 휴일이었는데 제안할 때는 말하지 않았다
            options.sort(Comparator.comparing(Scored::closed));
        }
        return propose(snapshot, kind, stop.stopId(), stop.day(), null, where, stop.name(), target, options);
    }

    private Outcome proposeAdd(Long userId, Long tripId, Snapshot snapshot, EditCommand command) {
        StopRef after = "START".equalsIgnoreCase(String.valueOf(command.afterRef())) ? null
                : snapshot.refs().get(normalizeRef(command.afterRef()));
        Integer day = command.day() != null ? command.day() : after == null ? null : after.day();
        DayView view = day == null ? null : snapshot.day(day);
        if (view == null) return Outcome.reply("몇 일차에 넣을지 알려주세요.");
        int position = "START".equalsIgnoreCase(String.valueOf(command.afterRef())) ? 0
                : after != null && after.day() == day ? after.index() + 1 : view.defaultAddPosition();

        String target = blankToNull(command.target());
        var found = stopEditService.addCandidates(userId, tripId, day, position);
        List<Scored> options = new ArrayList<>();
        for (var c : found.candidates()) {
            options.add(new Scored(c.id(), false, c.name(), c.nameKo(),
                    attractionDetail(c.labels(), c.travel(), c.fitsInDay()),
                    matchesPlace(target, c.name(), c.nameKo(), c.labels(), true), false));
        }
        return propose(snapshot, PendingEdit.ADD, null, day, position, "DAY" + day + "에 넣을 곳", null, target, options);
    }

    /**
     * 후보 중 원하는 것(target)과 맞는 곳을 앞에 두고 최대 3곳.
     * 원하는 게 있는데 맞는 곳이 없으면 그렇다고 말하고 다른 후보를 보여준다 (아무 곳이나 "바꿀까요?"라고 하지 않는다)
     */
    private Outcome propose(Snapshot snapshot, String kind, Long stopId, int day, Integer position, String where,
                            String before, String target, List<Scored> scored) {
        if (scored.isEmpty()) {
            return Outcome.reply("고를 수 있는 후보가 없어요. 다른 날이나 다른 항목을 말씀해 주세요.");
        }
        List<Scored> matched = scored.stream().filter(Scored::matches).toList();
        boolean exact = target != null && !matched.isEmpty();
        List<PendingEdit.Option> options = withKoreanNames((exact ? matched : scored).stream().limit(MAX_OPTIONS).toList());
        PendingEdit pending = new PendingEdit(kind, snapshot.version(), stopId, day, position, null, options, before);

        StringBuilder sb = new StringBuilder();
        if (target != null && !exact) {
            sb.append(where).append(" 근처에는 '").append(target).append("'에 맞는 곳이 없어요. 대신 이런 곳이 있어요.\n");
        }
        if (options.size() == 1 && exact) {
            sb.append(where).append(": ").append(before == null ? "" : before + " → ").append(options.get(0).label())
                    .append(before == null ? "\n여기에 넣을까요?" : "\n이렇게 바꿀까요?");
            return Outcome.reply(sb.toString(), pending);
        }
        if (sb.isEmpty()) sb.append(where).append(before == null ? "" : " (지금: " + before + ")").append('\n');
        for (int i = 0; i < options.size(); i++) {
            sb.append(i + 1).append(". ").append(options.get(i).label()).append('\n');
        }
        sb.append(options.size() == 1
                ? "이곳으로 할까요? '네' 또는 '그대로'라고 답해 주세요."
                : "번호로 골라 주세요. 바꾸지 않으려면 '그대로'라고 해 주세요.");
        return Outcome.reply(sb.toString(), pending);
    }

    private Outcome proposeDelete(Snapshot snapshot, EditCommand command) {
        StopRef stop = snapshot.refs().get(normalizeRef(command.stopRef()));
        if (stop == null) return Outcome.reply("어느 항목을 뺄지 찾지 못했어요. 몇 일차의 어떤 장소인지 알려주세요.");
        if (snapshot.day(stop.day()).stops().size() <= 1) {
            return Outcome.reply("DAY" + stop.day() + "의 마지막 항목이라 뺄 수 없어요. 다른 곳으로 바꾸는 건 할 수 있어요.");
        }
        PendingEdit pending = new PendingEdit(PendingEdit.DELETE, snapshot.version(), stop.stopId(), stop.day(),
                null, null, List.of(), stop.name());
        return Outcome.reply("DAY" + stop.day() + " " + stop.kindLabel() + " '" + stop.name()
                + "' 항목을 일정에서 뺄까요? 뺀 자리는 비워 두고 시간만 다시 계산해요.", pending);
    }

    private Outcome proposeMove(Snapshot snapshot, EditCommand command) {
        StopRef stop = snapshot.refs().get(normalizeRef(command.stopRef()));
        if (stop == null) return Outcome.reply("어느 항목을 옮길지 찾지 못했어요. 몇 일차의 어떤 장소인지 알려주세요.");
        boolean toStart = "START".equalsIgnoreCase(String.valueOf(command.afterRef()));
        StopRef after = toStart ? null : snapshot.refs().get(normalizeRef(command.afterRef()));
        if (!toStart && after == null) return Outcome.reply("어느 항목 뒤로 옮길지 알려주세요.");
        if (after != null && after.day() != stop.day()) {
            return Outcome.reply("다른 날로 옮기는 건 아직 할 수 없어요. 그날에서 빼고 다른 날에 넣는 방법은 있어요.");
        }
        List<StopRef> order = new ArrayList<>(snapshot.day(stop.day()).stops());
        order.remove(stop);
        order.add(toStart ? 0 : order.indexOf(after) + 1, stop);
        if (order.equals(snapshot.day(stop.day()).stops())) return Outcome.reply("이미 그 순서예요.");

        PendingEdit pending = new PendingEdit(PendingEdit.MOVE, snapshot.version(), stop.stopId(), stop.day(), null,
                order.stream().map(StopRef::stopId).toList(), List.of(), stop.name());
        StringBuilder sb = new StringBuilder("DAY" + stop.day() + " 순서를 이렇게 바꿀까요?\n");
        for (int i = 0; i < order.size(); i++) {
            sb.append(i + 1).append(". ").append(order.get(i).name()).append('\n');
        }
        return Outcome.reply(sb.append("점심·저녁 시간과 맞지 않으면 안내만 드리고 그대로 바꿔요.").toString(), pending);
    }

    /** 대기 중인 변경을 기존 수정 서비스로 적용한다. 서비스가 거절하면(400·404·409) 이유를 답으로 */
    private Outcome apply(Long userId, Long tripId, Snapshot snapshot, PendingEdit pending, PendingEdit.Option option) {
        if (pending.version() != snapshot.version()) {
            return Outcome.reply("그 사이에 일정이 바뀌어서 이 변경은 적용하지 않았어요. 다시 말씀해 주세요.");
        }
        try {
            ItineraryResponseDto result = switch (pending.kind()) {
                case PendingEdit.REPLACE_MEAL -> editService.replace(userId, tripId, pending.stopId(), option.id());
                case PendingEdit.REPLACE_ATTRACTION ->
                        attractionEditService.replace(userId, tripId, pending.stopId(), option.id());
                case PendingEdit.DELETE -> stopEditService.deleteStop(userId, tripId, pending.stopId());
                case PendingEdit.MOVE -> stopEditService.reorder(userId, tripId, pending.day(), pending.stopIds(),
                        pending.version());
                case PendingEdit.ADD -> stopEditService.addAttraction(userId, tripId, pending.day(), option.id(),
                        pending.position(), pending.version());
                default -> throw new IllegalArgumentException("알 수 없는 변경이에요.");
            };
            ItineraryResponseDto named = koreanNames.fill(result);
            log.info("[ChatEdit] 적용 tripId={} {} → 버전 {}", tripId, pending.kind(), named.version());
            return Outcome.applied(doneMessage(pending, option, named), named);
        } catch (IllegalArgumentException | ConflictException | NotFoundException e) {
            log.info("[ChatEdit] 적용 거절 tripId={} {}: {}", tripId, pending.kind(), e.getMessage());
            return Outcome.reply("바꾸지 못했어요. " + e.getMessage());
        }
    }

    static String doneMessage(PendingEdit pending, PendingEdit.Option option, ItineraryResponseDto result) {
        String what = switch (pending.kind()) {
            case PendingEdit.DELETE -> "DAY" + pending.day() + "에서 '" + pending.before() + "' 항목을 뺐어요.";
            case PendingEdit.MOVE -> "DAY" + pending.day() + " 순서를 바꿨어요.";
            case PendingEdit.ADD -> "DAY" + pending.day() + "에 " + nameOnly(option.label()) + " 항목을 넣었어요.";
            default -> "DAY" + pending.day() + " 항목을 바꿨어요: "
                    + (pending.before() == null ? "" : pending.before() + " → ") + nameOnly(option.label()) + ".";
        };
        StringBuilder sb = new StringBuilder(what).append(" 시간도 다시 계산했어요.");
        result.days().stream().filter(d -> d.dayNumber() == pending.day()).findFirst()
                .filter(d -> !d.warnings().isEmpty())
                .ifPresent(d -> sb.append("\n참고: ").append(d.warnings().get(0)));
        return sb.toString();
    }

    // ===================== 저장 =====================

    private ChatReplyResponseDto save(Long tripId, String content, Outcome outcome) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new NotFoundException("여행을 찾을 수 없습니다."));
        ChatMessage userMessage = chatMessageRepository.save(ChatMessage.builder()
                .trip(trip).role(ChatRole.USER).content(content).build());
        ChatMessage assistantMessage = chatMessageRepository.save(ChatMessage.builder()
                .trip(trip).role(ChatRole.ASSISTANT).content(outcome.reply())
                .pendingEdit(outcome.pending() == null ? null : jsonMapper.writeValueAsString(outcome.pending()))
                .build());
        return new ChatReplyResponseDto(ChatMessageResponseDto.from(userMessage),
                ChatMessageResponseDto.from(assistantMessage), false, false, trip.getStatus(),
                ChatReplyResponseDto.MODE_EDIT, outcome.pending() != null, outcome.itinerary());
    }

    // ===================== 지금 일정 읽기 =====================

    /** AI에게 줄 지금 일정(항목마다 D2-3 같은 표시), 대기 중인 변경, 최근 대화. 트랜잭션 안에서 */
    private Snapshot snapshot(Long tripId) {
        Itinerary latest = itineraryRepository.findFirstByTripIdOrderByVersionNoDesc(tripId)
                .orElseThrow(() -> new NotFoundException("아직 만들어진 일정이 없습니다."));
        List<DayView> days = new ArrayList<>();
        Map<String, StopRef> refs = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder("[현재 일정] (버전 ").append(latest.getVersionNo()).append(")\n");
        for (ItineraryDay day : latest.getDays()) {
            sb.append("DAY ").append(day.getDayNumber()).append(" (").append(day.getDate().getMonthValue()).append('/')
                    .append(day.getDate().getDayOfMonth()).append(", ").append(day.getZone().getLabel()).append(")\n");
            List<StopRef> stops = new ArrayList<>();
            for (int i = 0; i < day.getStops().size(); i++) {
                ItineraryStop stop = day.getStops().get(i);
                StopRef ref = toRef(day.getDayNumber(), i, stop);
                stops.add(ref);
                refs.put(ref.ref(), ref);
                sb.append("- ").append(ref.ref()).append(' ').append(ref.kindLabel()).append(' ').append(ref.name())
                        .append(ref.detail().isEmpty() ? "" : " (" + ref.detail() + ")").append('\n');
            }
            if (stops.isEmpty()) sb.append("(일정 없음)\n");
            days.add(new DayView(day.getDayNumber(), stops));
        }

        List<ChatMessage> recent = new ArrayList<>(chatMessageRepository.findTop10ByTripIdOrderByIdDesc(tripId));
        PendingEdit pending = null;
        if (!recent.isEmpty() && recent.get(0).getRole() == ChatRole.ASSISTANT && recent.get(0).getPendingEdit() != null) {
            pending = readPending(recent.get(0).getPendingEdit());
        }
        if (pending != null) {
            sb.append("\n[대기 중인 변경]\n").append(recent.get(0).getContent()).append('\n');
        }
        List<ChatMessage> lastFew = new ArrayList<>(recent.subList(0, Math.min(RECENT_MESSAGES, recent.size())));
        Collections.reverse(lastFew);
        sb.append("\n[최근 대화]\n");
        lastFew.forEach(m -> sb.append(m.getRole() == ChatRole.USER ? "사용자: " : "AI: ")
                .append(m.getContent().replace("<<<", "").replace(">>>", "")).append('\n'));
        return new Snapshot(latest.getVersionNo(), days, refs, sb.toString(), pending);
    }

    private StopRef toRef(int dayNumber, int index, ItineraryStop stop) {
        String ref = "D" + dayNumber + "-" + (index + 1);
        if (stop.getType() == StopType.ATTRACTION) {
            var a = stop.getAttraction();
            return new StopRef(ref, stop.getId(), dayNumber, index, stop.getType(), display(a.getNameKo(), a.getName()),
                    String.join(", ", typeLabels.toVisibleLabels(a.getTypes())));
        }
        var r = stop.getRestaurant();
        String genre = GenreLabels.joined(r.getGenre());
        return new StopRef(ref, stop.getId(), dayNumber, index, stop.getType(), display(r.getNameKo(), r.getName()),
                genre == null ? "" : genre);
    }

    private PendingEdit readPending(String json) {
        try {
            return jsonMapper.readValue(json, PendingEdit.class);
        } catch (JacksonException e) {
            log.warn("[ChatEdit] 대기 중인 변경을 읽지 못함: {}", e.getMessage());
            return null;
        }
    }

    // ===================== 도움 =====================

    /** "d2-3", "D2 - 3" → "D2-3" */
    static String normalizeRef(String ref) {
        if (ref == null) return null;
        Matcher m = REF.matcher(ref.toUpperCase(Locale.ROOT).replace(" ", ""));
        return m.find() ? "D" + m.group(1) + "-" + m.group(2) : null;
    }

    /** 원하는 말이 이름(한국어·원래)이나 종류·장르 이름에 들어 있는지 (띄어쓰기 무시) */
    /**
     * @param labelInTarget true면 사용자 말 안에 종류 이름이 들어 있어도 맞는 것으로 본다 ("조용한 정원" ⊃ "정원", 관광지용)
     */
    static boolean matchesPlace(String target, String name, String nameKo, List<String> labels, boolean labelInTarget) {
        if (target == null) return false;
        String t = squeeze(target);
        if (t.isEmpty()) return false;
        if (contains(name, t) || contains(nameKo, t)) return true;
        return labels != null && labels.stream().anyMatch(l -> contains(l, t)
                || (labelInTarget && l != null && squeeze(l).length() >= 2 && t.contains(squeeze(l))));
    }

    private static boolean contains(String text, String part) {
        return text != null && squeeze(text).contains(part);
    }

    private static String squeeze(String s) {
        return s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static Set<String> genresOf(String genre) {
        if (genre == null) return Set.of();
        Set<String> result = new java.util.HashSet<>();
        for (String g : genre.split(",")) if (!g.isBlank()) result.add(g.trim());
        return result;
    }

    private static String mealDetail(String genre, Double rating, ItineraryResponseDto.TravelDto travel,
                                     boolean overBudget, boolean closed) {
        List<String> parts = new ArrayList<>();
        String g = GenreLabels.joined(genre);
        if (g != null) parts.add(g);
        if (rating != null) parts.add("타베로그 " + String.format(Locale.ROOT, "%.2f", rating));
        if (travel != null) parts.add(moveLabel(travel));
        if (overBudget) parts.add("예산 조금 넘음");
        if (closed) parts.add("이 날 정기 휴일");
        return parts.isEmpty() ? "" : " (" + String.join(" · ", parts) + ")";
    }

    private static String attractionDetail(List<String> labels, ItineraryResponseDto.TravelDto travel, boolean fits) {
        List<String> parts = new ArrayList<>();
        if (labels != null && !labels.isEmpty()) parts.add(labels.get(0));
        if (travel != null) parts.add(moveLabel(travel));
        if (!fits) parts.add("시간이 넘칠 수 있어요");
        return parts.isEmpty() ? "" : " (" + String.join(" · ", parts) + ")";
    }

    private static String moveLabel(ItineraryResponseDto.TravelDto travel) {
        String how = switch (travel.mode()) {
            case WALK -> "걸어서";
            case DRIVE -> "차로";
            default -> "대중교통";
        };
        return how + " " + travel.minutes() + "분";
    }

    /** 명령이 대기 중인 변경과 같은 자리를 말하는지 (다른 날·다른 항목을 말하면 새 요청으로 본다). 말하지 않았으면 같다고 본다 */
    private static boolean samePlaceAsPending(Snapshot snapshot, PendingEdit pending, EditCommand command) {
        if (pending == null) return false;
        if (notBlank(command.stopRef())) {
            StopRef ref = snapshot.refs().get(normalizeRef(command.stopRef()));
            if (ref != null && pending.stopId() != null && !ref.stopId().equals(pending.stopId())) return false;
        }
        return command.day() == null || pending.day() == null || command.day().equals(pending.day());
    }

    /** 대기 중인 선택지 중 이름이 target과 맞는 것의 번호 (1부터). 없거나 여러 개면 null */
    static Integer optionNamed(PendingEdit pending, String target) {
        if (pending == null || pending.options() == null || !notBlank(target)) return null;
        String t = squeeze(target);
        Integer found = null;
        for (int i = 0; i < pending.options().size(); i++) {
            String name = squeeze(nameOnly(pending.options().get(i).label()));
            if (name.length() < 2 || t.length() < 2) continue;
            if (t.contains(name) || name.contains(t)) {
                if (found != null) return null;
                found = i + 1;
            }
        }
        return found;
    }

    /** 선택지 이름표에서 괄호 설명을 뺀 이름 */
    static String nameOnly(String label) {
        int at = label.lastIndexOf(" (");
        return at > 0 ? label.substring(0, at) : label;
    }

    private static String display(String nameKo, String name) {
        return nameKo == null || nameKo.isBlank() ? name : nameKo;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return notBlank(s) ? s.strip() : null;
    }

    // ===================== 내부 자료 =====================

    record StopRef(String ref, Long stopId, int day, int index, StopType type, String name, String detail) {
        String kindLabel() {
            return switch (type) {
                case LUNCH -> "점심";
                case DINNER -> "저녁";
                default -> "관광지";
            };
        }
    }

    record DayView(int day, List<StopRef> stops) {
        /** 넣을 자리를 말하지 않으면 저녁 앞 (저녁이 없으면 맨 끝) */
        int defaultAddPosition() {
            for (int i = 0; i < stops.size(); i++) {
                if (stops.get(i).type() == StopType.DINNER) return i;
            }
            return stops.size();
        }
    }

    record Snapshot(int version, List<DayView> days, Map<String, StopRef> refs, String text, PendingEdit pending) {
        DayView day(int number) {
            return days.stream().filter(d -> d.day() == number).findFirst().orElse(null);
        }
    }

    /** 후보 하나 (이름표는 한국어 이름을 채운 뒤에 만든다) */
    private record Scored(Long id, boolean restaurant, String name, String nameKo, String detail, boolean matches,
                          boolean closed) {
    }

    /** 보여줄 후보의 한국어 이름이 없으면 만들어서(저장됨) 이름표를 만든다. 실패하면 원래 이름 */
    private List<PendingEdit.Option> withKoreanNames(List<Scored> shown) {
        Map<Long, String> attractions = new LinkedHashMap<>();
        Map<Long, String> restaurants = new LinkedHashMap<>();
        shown.stream().filter(c -> c.nameKo() == null || c.nameKo().isBlank())
                .forEach(c -> (c.restaurant() ? restaurants : attractions).put(c.id(), c.name()));
        KoreanNameService.Names made = attractions.isEmpty() && restaurants.isEmpty()
                ? new KoreanNameService.Names(Map.of(), Map.of())
                : koreanNames.namesFor(attractions, restaurants);
        return shown.stream().map(c -> {
            String ko = c.nameKo() != null && !c.nameKo().isBlank() ? c.nameKo()
                    : (c.restaurant() ? made.restaurants() : made.attractions()).get(c.id());
            return new PendingEdit.Option(c.id(), display(ko, c.name()) + c.detail());
        }).toList();
    }

    record Outcome(String reply, PendingEdit pending, ItineraryResponseDto itinerary) {
        static Outcome reply(String reply) {
            return new Outcome(reply, null, null);
        }

        static Outcome reply(String reply, PendingEdit pending) {
            return new Outcome(reply, pending, null);
        }

        static Outcome applied(String reply, ItineraryResponseDto itinerary) {
            return new Outcome(reply, null, itinerary);
        }
    }
}
