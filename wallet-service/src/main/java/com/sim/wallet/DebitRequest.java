package com.sim.wallet;

import java.math.BigDecimal;

public record DebitRequest(String simulationId, int requestNo, int totalRequests,
                           long rowId, BigDecimal amount, int holdMs, int cpuBurnMs) {
}
