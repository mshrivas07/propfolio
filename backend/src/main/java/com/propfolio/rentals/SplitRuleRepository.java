package com.propfolio.rentals;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SplitRuleRepository extends JpaRepository<SplitRule, UUID> {
}
