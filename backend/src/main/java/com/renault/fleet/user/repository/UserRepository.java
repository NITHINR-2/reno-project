package com.renault.fleet.user.repository;

import com.renault.fleet.user.entity.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    @Override
    @EntityGraph(attributePaths = "role")
    Page<User> findAll(Specification<User> spec, Pageable pageable);

    @EntityGraph(attributePaths = "role")
    Optional<User> findWithRoleById(Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

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
