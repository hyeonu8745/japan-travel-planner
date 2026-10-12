package com.example.japantravelplanner.poster;

import com.example.japantravelplanner.trip.entity.Trip;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 포스터 한 묶음 (지도 포스터 1장 + 상세 일정표 여러 장). 어느 일정 버전으로 만들었는지 남긴다 */
@Entity
@Table(name = "trip_posters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPoster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "itinerary_version", nullable = false)
    private int itineraryVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PosterStatus status;

    @Column(length = 50)
    private String model;

    @Column(name = "error_message", length = 300)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @OneToMany(mappedBy = "poster", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("pageNo ASC")
    private List<TripPosterPage> pages = new ArrayList<>();

    public static TripPoster start(Trip trip, int itineraryVersion, String model, LocalDateTime now) {
        TripPoster p = new TripPoster();
        p.trip = trip;
        p.itineraryVersion = itineraryVersion;
        p.status = PosterStatus.GENERATING;
        p.model = model;
        p.createdAt = now;
        return p;
    }

    public void addPage(TripPosterPage page) {
        pages.add(page);
        page.attach(this);
    }

    public void finish(PosterStatus result, String error, LocalDateTime now) {
        this.status = result;
        this.errorMessage = error == null || error.length() <= 300 ? error : error.substring(0, 300);
        this.finishedAt = now;
    }
}
