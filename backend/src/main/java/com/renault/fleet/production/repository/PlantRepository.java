package com.renault.fleet.production.repository;

import com.renault.fleet.production.entity.Plant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantRepository extends JpaRepository<Plant, Long> {
}
