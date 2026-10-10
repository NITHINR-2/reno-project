package com.renault.fleet.quotation.repository;

import com.renault.fleet.quotation.entity.TaxRate;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {

    /** The GST rate in force for a category on a date (periods cannot overlap, so at most one row matches). */
    @Query("""
            select t from TaxRate t
            where t.vehicleCategory = :category and t.taxType = 'GST'
              and t.effectiveFrom <= :on and (t.effectiveTo is null or t.effectiveTo > :on)
            """)
    Optional<TaxRate> findEffective(@Param("category") String category, @Param("on") LocalDate on);
}
