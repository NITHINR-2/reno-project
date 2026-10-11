package com.renault.fleet.inventory.repository;

import com.renault.fleet.inventory.entity.InventoryStock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryStockRepository extends JpaRepository<InventoryStock, Long>, JpaSpecificationExecutor<InventoryStock> {

    /** {@code SELECT ... FOR UPDATE}: must be called inside the allocation transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InventoryStock s where s.id = :id")
    Optional<InventoryStock> findByIdForUpdate(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"location", "variant", "variant.model"})
    Page<InventoryStock> findAll(Specification<InventoryStock> spec, Pageable pageable);

    /** Free units a request could use: same variant, same colour (both null, or the same row), active location. */
    @Query("""
            select coalesce(sum(s.quantityAvailable), 0) from InventoryStock s left join s.colour c
            where s.variant.id = :variantId and s.location.active = true
              and ((:colourId is null and c.id is null) or c.id = :colourId)
            """)
    long sumEligibleAvailable(@Param("variantId") Long variantId, @Param("colourId") Long colourId);

    /** The uncoloured stock row at a location (HQ yard stock). */
    Optional<InventoryStock> findByLocationIdAndVariantIdAndColourIsNull(Long locationId, Long variantId);

    Optional<InventoryStock> findByLocationIdAndVariantIdAndColourId(Long locationId, Long variantId, Long colourId);

    /** Stock rows of a variant and colour at a plant's active locations, in id (= lock) order. */
    @Query("""
            select s.id from InventoryStock s left join s.colour c
            where s.location.plant.id = :plantId and s.location.active = true and s.variant.id = :variantId
              and ((:colourId is null and c.id is null) or c.id = :colourId)
            order by s.id
            """)
    List<Long> findPlantStockIds(@Param("plantId") Long plantId, @Param("variantId") Long variantId,
            @Param("colourId") Long colourId);

    /** Free units per variant and colour at a plant's active locations (the dealer catalogue's availability). */
    @Query("""
            select s.variant.id as variantId, c.id as colourId, sum(s.quantityAvailable) as available
            from InventoryStock s left join s.colour c
            where s.location.plant.id = :plantId and s.location.active = true
            group by s.variant.id, c.id
            """)
    List<PlantAvailability> availabilityAtPlant(@Param("plantId") Long plantId);
}
