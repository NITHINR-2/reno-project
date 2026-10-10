package com.renault.fleet.inventory.repository;

import com.renault.fleet.inventory.entity.StockAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAllocationRepository extends JpaRepository<StockAllocation, Long> {
}
