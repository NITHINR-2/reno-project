package com.renault.fleet.sla.repository;

import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.sla.entity.SlaPolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    Optional<SlaPolicy> findByStage(FleetRequestStatus stage);
}
