package com.example.japantravelplanner.user.token;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** 같은 토큰으로 거의 동시에 갱신해도 한 번만 바뀌게 행을 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 사용자의 살아 있는 토큰 (family 마다 하나) */
    @Query("select t from RefreshToken t where t.userId = :userId and t.revokedAt is null and t.expiresAt > :now order by t.createdAt")
    List<RefreshToken> findActive(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("now") Instant now, @Param("reason") String reason);

    /** 만료된 지 오래된 기록 정리 (로그인할 때 그 사용자 것만) */
    @Modifying
    @Query("delete from RefreshToken t where t.userId = :userId and t.expiresAt < :before")
    int deleteExpired(@Param("userId") Long userId, @Param("before") Instant before);
}
