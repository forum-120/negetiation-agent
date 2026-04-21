package com.nego.simulator.policy;

import com.nego.simulator.model.AnomalyRecord;
import java.util.List;

public interface PolicyRule {
    List<AnomalyRecord> check(PolicyContext ctx);
}