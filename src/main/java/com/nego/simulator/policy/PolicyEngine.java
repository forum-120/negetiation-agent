package com.nego.simulator.policy;

import com.nego.simulator.model.AnomalyRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PolicyEngine {

    private final List<PolicyRule> rules;

    public PolicyEngine(List<PolicyRule> rules) {
        this.rules = rules;
    }

    public List<AnomalyRecord> evaluate(PolicyContext ctx) {
        List<AnomalyRecord> results = new ArrayList<>();
        for (PolicyRule rule : rules) {
            results.addAll(rule.check(ctx));
        }
        return results;
    }
}