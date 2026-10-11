package com.renault.fleet.dealerportal.repository;

import com.renault.fleet.dealerportal.entity.DealerOrder;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface DealerOrderRepository extends JpaRepository<DealerOrder, Long>, JpaSpecificationExecutor<DealerOrder> {

    /** Scoped lookup: another dealer's order is indistinguishable from a missing one. */
    Optional<DealerOrder> findByIdAndDealerId(Long id, Long dealerId);

    @Query(value = "select nextval('dealer_order_number_seq')", nativeQuery = true)
    long nextOrderNumber();
}
