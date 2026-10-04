package com.example.japantravelplanner.itinerary.entity;

import com.example.japantravelplanner.itinerary.zone.TripZone;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** 일정의 하루. 지역, 가능한 시간대, 하루 설명, 항목 목록, 빠진 장소와 안내 문장을 가진다 */
@Entity
@Table(name = "itinerary_days",
        indexes = @Index(name = "idx_itinerary_days_itinerary", columnList = "itinerary_id, day_number"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItineraryDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private Itinerary itinerary;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(name = "day_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TripZone zone;

    // 이 날 일정을 넣을 수 있었던 시간대 (도착·출발 시각을 반영). 일정을 넣을 수 없는 날은 null
    @Column(name = "window_start")
    private LocalTime windowStart;

    @Column(name = "window_end")
    private LocalTime windowEnd;

    // AI가 쓴 하루 설명. 생성에 실패했거나 규칙을 어겨서 버려졌으면 null
    @Column(length = 1000)
    private String description;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stopOrder ASC")
    @BatchSize(size = 50)
    private List<ItineraryStop> stops = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "itinerary_day_dropped", joinColumns = @JoinColumn(name = "day_id"))
    @BatchSize(size = 50)
    private List<DroppedPlace> dropped = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "itinerary_day_warnings", joinColumns = @JoinColumn(name = "day_id"))
    @Column(name = "warning", length = 300)
    @BatchSize(size = 50)
    private List<String> warnings = new ArrayList<>();

    public static ItineraryDay create(int dayNumber, LocalDate date, TripZone zone,
                                      LocalTime windowStart, LocalTime windowEnd, String description,
                                      List<DroppedPlace> dropped, List<String> warnings) {
        ItineraryDay day = new ItineraryDay();
        day.dayNumber = dayNumber;
        day.date = date;
        day.zone = zone;
        day.windowStart = windowStart;
        day.windowEnd = windowEnd;
        day.description = description;
        day.dropped.addAll(dropped);
        day.warnings.addAll(warnings);
        return day;
    }

    public void addStop(ItineraryStop stop) {
        stops.add(stop);
        stop.assignTo(this);
    }

    // 같은 패키지의 Itinerary.addDay에서만 호출
    void assignTo(Itinerary itinerary) {
        this.itinerary = itinerary;
    }
}