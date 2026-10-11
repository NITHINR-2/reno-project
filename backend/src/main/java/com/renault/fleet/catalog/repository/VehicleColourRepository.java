package com.renault.fleet.catalog.repository;

import com.renault.fleet.catalog.entity.VehicleColour;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleColourRepository extends JpaRepository<VehicleColour, Long> {

    List<VehicleColour> findByModelIdInOrderByName(Collection<Long> modelIds);

    @Query("select count(c) > 0 from VehicleColour c where c.model.id = :modelId and lower(c.name) = lower(:name)"
            + " and (:excludeId is null or c.id <> :excludeId)")
    boolean nameTaken(@Param("modelId") Long modelId, @Param("name") String name, @Param("excludeId") Long excludeId);
}
