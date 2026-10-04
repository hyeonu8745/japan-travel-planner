package com.example.japantravelplanner.itinerary.entity;

import com.example.japantravelplanner.trip.entity.Trip;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

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

    @OneToMany(mappedBy = "itinerary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    @BatchSize(size = 20)
    private List<ItineraryDay> days = new ArrayList<>();

    public static Itinerary create(Trip trip, int versionNo, double krwPerYen) {
        Itinerary itinerary = new Itinerary();
        itinerary.trip = trip;
        itinerary.versionNo = versionNo;
        itinerary.krwPerYen = krwPerYen;
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