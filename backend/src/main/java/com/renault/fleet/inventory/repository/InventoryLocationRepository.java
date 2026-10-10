package com.renault.fleet.inventory.repository;

import com.renault.fleet.inventory.entity.InventoryLocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryLocationRepository extends JpaRepository<InventoryLocation, Long> {
}
