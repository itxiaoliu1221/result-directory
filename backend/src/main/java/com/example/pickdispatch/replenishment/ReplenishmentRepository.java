package com.example.pickdispatch.replenishment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReplenishmentRepository extends JpaRepository<Replenishment, Long> {

    Optional<Replenishment> findByRequestNo(String requestNo);

    @EntityGraph(attributePaths = "items")
    List<Replenishment> findTop20ByOrderByCreatedAtDesc();
}
