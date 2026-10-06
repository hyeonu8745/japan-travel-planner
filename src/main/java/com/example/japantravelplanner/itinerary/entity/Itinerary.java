package com.example.japantravelplanner.itinerary.entity;

import com.example.japantravelplanner.trip.entity.Trip;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 완성된 일정 한 벌. 여행 하나에 버전이 여러 개 쌓인다 (다시 만들면 새 버전, 이전 버전은 그대로).
 * 생성 후에는 바뀌지 않는다. Setter 없이 create()로만 만든다.
 */
@Entity
@Table(name = "itineraries",
        uniqueConstraints = @UniqueConstraint(name = "uk_itineraries_trip_version",
                columnNames = {"trip_id", "version_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Itinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    // 일정을 만들 때 쓴 환율 (100엔 = 900원이면 9.0). 나중에 환율이 바뀌어도 이 일정의 금액 계산은 그대로
    @Column(name = "krw_per_yen", nullable = false)
    private double krwPerYen;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 이 버전이 만들어진 방식. 컬럼을 나중에 추가해도 기존 일정이 모두 GENERATED가 되도록 기본값을 둔다
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    @ColumnDefault("'GENERATED'")
    private ItinerarySource source = ItinerarySource.GENERATED;

    // 변경 이력에 보여줄 한 줄 설명 ("DAY2 점심: ザ・カリ → つじ田"). 옛 일정은 비어 있다
    @Column(name = "change_note", length = 300)
    private String changeNote;

    @OneToMany(mappedBy = "itinerary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    @BatchSize(size = 20)
    private List<ItineraryDay> days = new ArrayList<>();

    /** AI로 새로 생성한 일정 */
    public static Itinerary create(Trip trip, int versionNo, double krwPerYen) {
        return create(trip, versionNo, krwPerYen, ItinerarySource.GENERATED);
    }

    public static Itinerary create(Trip trip, int versionNo, double krwPerYen, ItinerarySource source) {
        return create(trip, versionNo, krwPerYen, source, null);
    }

    public static Itinerary create(Trip trip, int versionNo, double krwPerYen, ItinerarySource source,
                                   String changeNote) {
        Itinerary itinerary = new Itinerary();
        itinerary.trip = trip;
        itinerary.versionNo = versionNo;
        itinerary.krwPerYen = krwPerYen;
        itinerary.source = source;
        itinerary.changeNote = changeNote == null || changeNote.length() <= 300
                ? changeNote : changeNote.substring(0, 300);
        return itinerary;
    }

    public void addDay(ItineraryDay day) {
        days.add(day);
        day.assignTo(this);
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}