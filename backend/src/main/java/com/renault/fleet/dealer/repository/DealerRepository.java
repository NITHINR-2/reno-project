package com.renault.fleet.dealer.repository;

import com.renault.fleet.dealer.entity.Dealer;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DealerRepository extends JpaRepository<Dealer, Long>, JpaSpecificationExecutor<Dealer> {

    @Override
    @EntityGraph(attributePaths = "region")
    Page<Dealer> findAll(Specification<Dealer> spec, Pageable pageable);

    @EntityGraph(attributePaths = "region")
    Optional<Dealer> findWithRegionById(Long id);

    Optional<Dealer> findByDealerCode(String dealerCode);

    boolean existsByDealerCodeIgnoreCase(String dealerCode);
}
