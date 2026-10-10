package com.renault.fleet.inventory.repository;

import com.renault.fleet.inventory.entity.InventoryStock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryStockRepository extends JpaRepository<InventoryStock, Long> {

    /** {@code SELECT ... FOR UPDATE}: must be called inside the allocation transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InventoryStock s where s.id = :id")
    Optional<InventoryStock> findByIdForUpdate(@Param("id") Long id);
}
