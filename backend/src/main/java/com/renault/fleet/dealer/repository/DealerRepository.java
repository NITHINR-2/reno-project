package com.renault.fleet.dealer.repository;

import com.renault.fleet.dealer.entity.Dealer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealerRepository extends JpaRepository<Dealer, Long> {
}
