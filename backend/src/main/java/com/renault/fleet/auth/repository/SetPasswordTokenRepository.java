package com.renault.fleet.auth.repository;

import com.renault.fleet.auth.entity.SetPasswordToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SetPasswordTokenRepository extends JpaRepository<SetPasswordToken, Long> {

    @Query("select t from SetPasswordToken t join fetch t.user u join fetch u.role where t.tokenHash = :hash")
    Optional<SetPasswordToken> findByTokenHash(@Param("hash") String hash);

    /** Row lock so two concurrent submissions of one link can't both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SetPasswordToken t where t.tokenHash = :hash")
    Optional<SetPasswordToken> findByTokenHashForUpdate(@Param("hash") String hash);

    /** Revokes every still-open link of a user (a new link replaces older ones; a password set spends them all). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SetPasswordToken t set t.revokedAt = :now, t.version = t.version + 1"
            + " where t.user.id = :userId and t.usedAt is null and t.revokedAt is null")
    int revokeOpenForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
