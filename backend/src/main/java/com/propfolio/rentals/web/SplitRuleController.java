package com.propfolio.rentals.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.propfolio.rentals.SplitRuleService;
import com.propfolio.rentals.web.SplitRuleDtos.SplitRuleRequest;
import com.propfolio.rentals.web.SplitRuleDtos.SplitRuleResponse;

/**
 * Split rules. PUT creates or replaces the rule for a unit + utility account pair.
 * Rules are read through GET /api/properties/{id}.
 */
@RestController
@RequestMapping("/api/split-rules")
public class SplitRuleController {

    private final SplitRuleService splitRuleService;

    public SplitRuleController(SplitRuleService splitRuleService) {
        this.splitRuleService = splitRuleService;
    }

    @PutMapping
    public SplitRuleResponse save(@Valid @RequestBody SplitRuleRequest request) {
        return splitRuleService.save(request);
    }

    @DeleteMapping("/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID ruleId) {
        splitRuleService.delete(ruleId);
    }
}
