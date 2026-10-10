package com.renault.fleet.user.repository;

import com.renault.fleet.user.entity.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u join fetch u.role r left join fetch r.permissions where u.id = :id")
    Optional<User> findWithAuthoritiesById(@Param("id") Long id);

    @Query("select u from User u join fetch u.role r left join fetch r.permissions where lower(u.username) = lower(:username)")
    Optional<User> findWithAuthoritiesByUsername(@Param("username") String username);

    boolean existsByUsernameIgnoreCase(String username);

    @Transactional
    @Modifying
    @Query("update User u set u.lastLoginAt = :at where u.id = :id")
    int updateLastLoginAt(@Param("id") Long id, @Param("at") Instant at);
}
