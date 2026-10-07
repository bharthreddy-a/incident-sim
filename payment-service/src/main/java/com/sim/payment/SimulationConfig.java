package com.sim.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "simulation")
public class SimulationConfig {
    private String walletBaseUrl = "http://localhost:8081";
    private String configFile = "./config/simulation.json";
    private int requests = 100;
    private int concurrency = 20;
    private List<Long> rows = List.of(1L, 2L);
    private int holdMs = 2000;
    private int cpuBurnMs = 0;
    private int memoryPerResponseKb = 5120;
    private BigDecimal amount = BigDecimal.ONE;
    private int walletTimeoutMs = 60000;
}
