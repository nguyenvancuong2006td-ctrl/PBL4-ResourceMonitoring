package com.pbl4.server.repository;

import com.pbl4.server.model.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRuleRepository
        extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByEnabledTrue();
}