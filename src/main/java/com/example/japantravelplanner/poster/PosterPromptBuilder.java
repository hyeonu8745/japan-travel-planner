package com.example.japantravelplanner.poster;

import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto.DayDto;
import com.example.japantravelplanner.itinerary.dto.ItineraryResponseDto.StopDto;
import com.example.japantravelplanner.itinerary.planner.StopType;
import com.example.japantravelplanner.itinerary.travel.LegMode;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 일정 → 이미지 AI에 보낼 글 (10-12). 서버가 정한 글자(제목, DAY, 장소 이름, 시각)를 그대로 쓰게 하고
 * 목록에 없는 장소·글자를 넣지 않게 한다 (계산은 서버, AI는 그리기만 — 하루 설명과 같은 원칙).
 * 장소 이름은 한국어 이름(없으면 원래 이름), 식당은 음식 종류(장르 한국어 이름)로 쓴다.
 */
public final class PosterPromptBuilder {

    private PosterPromptBuilder() {
    }

    /** 한 장의 내용 */
    public record PageSpec(int pageNo, PosterPageKind kind, String size, String prompt) {
    }

    public static List<PageSpec> build(ItineraryResponseDto itinerary, String title, LocalDate startDate,
                                       PosterProperties props) {
        List<DayDto> days = itinerary.days().stream().filter(d -> !d.stops().isEmpty()).toList();
        int detailPages = (days.size() + props.getDaysPerDetailPage() - 1) / Math.max(1, props.getDaysPerDetailPage());
        int total = 1 + detailPages;
        String season = season(startDate);

        List<PageSpec> pages = new ArrayList<>();
        String coverSize = days.size() >= 5 && supportsCustomSize(props.getModel()) ? props.getLongCoverSize() : props.getCoverSize();
        pages.add(new PageSpec(1, PosterPageKind.COVER, coverSize, cover(days, title, season, total, props)));
        for (int i = 0; i < detailPages; i++) {
            int from = i * props.getDaysPerDetailPage();
            List<DayDto> chunk = days.subList(from, Math.min(days.size(), from + props.getDaysPerDetailPage()));
            boolean last = i == detailPages - 1;
            pages.add(new PageSpec(i + 2, PosterPageKind.DETAIL, props.getDetailSize(),
                    detail(chunk, title, season, i + 2, total, last)));
        }
        return pages;
    }

    // ===================== 1장: 지도 포스터 =====================

    static String cover(List<DayDto> days, String title, String season, int total, PosterProperties props) {
        StringBuilder sb = new StringBuilder();
        sb.append("A vertical illustrated travel map poster of Tokyo, Japan, in a hand-drawn watercolor and ink style.\n");
        sb.append("Season and mood: ").append(season).append(".\n");
        sb.append("A winding path runs from top to bottom through the poster, connecting one illustrated section per day. ");
        sb.append("In each section, draw small illustrations of the listed places in order, connected by arrows, with a rounded white label under each illustration. ");
        sb.append("An original cute round mascot character with a small backpack walks along the path (an original design, not any existing character or brand mascot).\n\n");
        sb.append(TEXT_RULES);
        sb.append("\nTitle at the top, large and bold: \"").append(title).append(" 여행 지도\"\n\n");
        for (DayDto day : days) {
            sb.append("Section: \"DAY ").append(day.dayNumber()).append(" · ").append(areaLabel(day)).append("\"\n");
            sb.append("  Places in order (label text exactly; the part after 'draw:' is only a hint for the picture, never write it):\n");
            for (StopDto s : coverStops(day, props.getCoverItemsPerDay())) {
                sb.append("    - \"").append(label(s)).append("\"  draw: ").append(drawingHint(s)).append('\n');
            }
            String moves = moves(day);
            if (moves != null) sb.append("  Small transport badge: \"이동 · ").append(moves).append("\"\n");
        }
        sb.append("\nFooter, small: \"TabiMate · 실제 운영 정보는 방문 전 확인 · 01\"\n");
        sb.append(DRAWING_RULES);
        return sb.toString();
    }

    /**
     * 하루의 대표 장소 (일정 순서 그대로): 식당은 한 곳만(저녁 우선, 음식 종류로), 그래도 많으면
     * 마지막 장소(야경 등)는 남기고 뒤쪽 관광지부터 뺀다
     */
    static List<String> coverItems(DayDto day, int max) {
        Set<String> labels = new LinkedHashSet<>();
        coverStops(day, max).forEach(s -> labels.add(label(s)));
        return List.copyOf(labels);
    }

    static List<StopDto> coverStops(DayDto day, int max) {
        List<StopDto> list = new ArrayList<>(day.stops());
        List<StopDto> meals = list.stream().filter(s -> s.type() != StopType.ATTRACTION).toList();
        if (meals.size() > 1) {
            StopDto keep = meals.stream().filter(s -> s.type() == StopType.DINNER).findFirst().orElse(meals.get(0));
            list.removeIf(s -> s.type() != StopType.ATTRACTION && s != keep);
        }
        while (list.size() > max && list.size() > 1) {
            int remove = -1;
            for (int i = list.size() - 2; i >= 0; i--) {
                if (list.get(i).type() == StopType.ATTRACTION) { remove = i; break; }
            }
            if (remove < 0) remove = list.size() - 2;
            list.remove(remove);
        }
        return list;
    }

    /**
     * 그림 힌트: 원래 이름(일본어 등)과 종류. 한국어 이름만 주면 엉뚱하게 그린다
     * (10-12 첫 포스터: "슛세노 이시단"(出世の石段, 신사 돌계단)을 스테이크 접시로 그렸다)
     */
    static String drawingHint(StopDto s) {
        if (s.type() != StopType.ATTRACTION) {
            String genre = s.restaurant() != null && s.restaurant().genre() != null ? s.restaurant().genre() : label(s);
            return "a dish of " + genre;
        }
        String kind = s.attraction() != null && s.attraction().labels() != null && !s.attraction().labels().isEmpty()
                ? " (" + String.join(", ", s.attraction().labels()) + ")" : "";
        return s.name().replace("\"", "") + kind;
    }

    // ===================== 2장~: 상세 일정표 =====================

    static String detail(List<DayDto> days, String title, String season, int pageNo, int total, boolean last) {
        StringBuilder sb = new StringBuilder();
        sb.append("A vertical printable travel itinerary page, clean and simple, in the same hand-drawn watercolor style as a Tokyo travel poster. ");
        sb.append("Mostly white or cream background so the text is easy to read. Season accents: ").append(season).append(".\n");
        sb.append("Layout: a small header ribbon at the top, then one rounded card per day. Inside each card, a timetable with three columns: time in a small pill, place name in bold, a short note in lighter text. ");
        sb.append("Only small decorative illustrations at the edges (no large pictures). The same original cute round mascot may appear once, small.\n\n");
        sb.append(TEXT_RULES);
        sb.append("\nHeader: \"").append(title).append(" · 상세 일정\"\n\n");
        for (DayDto day : days) {
            sb.append("Card title: \"DAY ").append(day.dayNumber()).append(" · ").append(dateLabel(day.date()))
                    .append(" · ").append(areaLabel(day)).append("\"\n");
            for (StopDto s : day.stops()) {
                sb.append("  Row: \"").append(s.arrival() == null ? "" : s.arrival().toString()).append("\" | \"")
                        .append(label(s)).append("\" | \"").append(note(s)).append("\"\n");
            }
        }
        if (last) {
            sb.append("\nSmall note box at the bottom: \"이동 시간은 예상치예요. 영업시간·휴무와 교통편은 방문 전 확인해 주세요.\"\n");
        }
        sb.append(String.format("%nFooter, small: \"TabiMate · %s · %02d\"%n", title, pageNo));
        sb.append(DRAWING_RULES);
        return sb.toString();
    }

    static String note(StopDto s) {
        String meal = switch (s.type()) {
            case LUNCH -> "점심";
            case DINNER -> "저녁";
            default -> null;
        };
        if (meal != null) {
            return meal + " · " + displayName(s);   // 이름 칸이 음식 종류라서 메모에 식당 이름
        }
        String kind = s.attraction() != null && s.attraction().labels() != null && !s.attraction().labels().isEmpty()
                ? s.attraction().labels().get(0) : "관광";
        return kind + " · " + s.stayMinutes() + "분";
    }

    // ===================== 공통 =====================

    static final String TEXT_RULES = """
            TEXT RULES (very important):
            - Write every text in double quotes below exactly as given, character by character, in Korean. Do not translate, shorten or reorder it.
            - Do not add any other words, place names, prices, phone numbers, logos or brand names.
            - Draw only the places listed. Do not add other famous landmarks as labeled places.
            """;

    static final String DRAWING_RULES = """

            STYLE: soft pastel watercolor, warm and friendly, clean readable Korean typography in rounded handwriting style, high legibility, no photorealism, no real people, no copyrighted characters.
            """;

    /** 지도·일정표에 쓰는 이름: 관광지는 한국어 이름, 식당은 음식 종류 (예: "츠케멘") */
    static String label(StopDto s) {
        if (s.type() != StopType.ATTRACTION && s.restaurant() != null && s.restaurant().genreLabels() != null
                && !s.restaurant().genreLabels().isEmpty()) {
            return s.restaurant().genreLabels().get(0);
        }
        return displayName(s);
    }

    static String displayName(StopDto s) {
        String name = s.nameKo() != null && !s.nameKo().isBlank() ? s.nameKo() : s.name();
        return name.replace("\"", "").strip();
    }

    static String areaLabel(DayDto day) {
        return day.zonesLabel() != null ? day.zonesLabel() : day.zoneLabel();
    }

    static String dateLabel(LocalDate date) {
        if (date == null) return "";
        return date.getMonthValue() + "/" + date.getDayOfMonth() + " (" + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
    }

    /** 그날 쓰는 이동 수단 ("대중교통, 도보") */
    static String moves(DayDto day) {
        Set<String> modes = new LinkedHashSet<>();
        for (StopDto s : day.stops()) {
            if (s.travel() == null) continue;
            LegMode m = s.travel().mode();
            modes.add(m == LegMode.WALK ? "도보" : m == LegMode.DRIVE ? "렌터카" : "대중교통");
        }
        return modes.isEmpty() ? null : String.join(", ", modes);
    }

    static String season(LocalDate start) {
        int m = start == null ? LocalDate.now().getMonthValue() : start.getMonthValue();
        if (m == 12 || m <= 2) return "winter, light snow, cozy scarves";
        if (m <= 5) return "spring, cherry blossoms, fresh green";
        if (m <= 8) return "summer, blue sky, bright sunshine";
        return "autumn, red and yellow maple and ginkgo leaves, clear sky";
    }

    static boolean supportsCustomSize(String model) {
        return model != null && model.startsWith("gpt-image-2");
    }
}
