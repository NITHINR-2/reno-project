package com.renault.fleet.dispatch.repository;

import com.renault.fleet.dispatch.entity.Dispatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchRepository extends JpaRepository<Dispatch, Long> {
}
