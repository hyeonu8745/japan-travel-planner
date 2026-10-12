package com.example.japantravelplanner.poster;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TripPosterRepository extends JpaRepository<TripPoster, Long> {

    Optional<TripPoster> findFirstByTripIdOrderByIdDesc(Long tripId);

    Optional<TripPoster> findByIdAndTripId(Long id, Long tripId);

    List<TripPoster> findByTripId(Long tripId);

    long countByTripIdAndCreatedAtAfter(Long tripId, LocalDateTime after);

    /**
     * 하루 횟수에 세는 묶음: 그리는 중이거나 한 장이라도 그려진 것 (10-12).
     * 서버 재시작 등으로 한 장도 못 그리고 실패한 묶음은 세지 않는다
     */
    @Query("select count(p) from TripPoster p where p.trip.id = :tripId and p.createdAt > :after "
            + "and exists (select pg.id from TripPosterPage pg where pg.poster = p "
            + "and pg.status <> com.example.japantravelplanner.poster.PosterStatus.FAILED)")
    long countUsed(@Param("tripId") Long tripId, @Param("after") LocalDateTime after);

    List<TripPoster> findByStatus(PosterStatus status);
}
