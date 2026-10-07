package com.sim.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SimulationService {
    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final ObjectMapper mapper;
    private final SimulationConfig defaults;
    private final MemoryHog memoryHog;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private volatile String state = "IDLE";
    private volatile String simulationId;
    private volatile SimulationConfig active;
    private final AtomicInteger sent = new AtomicInteger();
    private final AtomicInteger ok = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    public SimulationService(ObjectMapper mapper, SimulationConfig defaults, MemoryHog memoryHog, MeterRegistry registry) {
        this.mapper = mapper;
        this.defaults = defaults;
        this.memoryHog = memoryHog;
        Gauge.builder("sim.wallet.requests.sent", sent, AtomicInteger::get).register(registry);
        Gauge.builder("sim.wallet.requests.ok", ok, AtomicInteger::get).register(registry);
        Gauge.builder("sim.wallet.requests.failed", failed, AtomicInteger::get).register(registry);
    }

    /** yml defaults -> local json file -> request body */
    public synchronized Map<String, Object> start(JsonNode body) throws Exception {
        if ("RUNNING".equals(state)) throw new IllegalStateException("A simulation is already running: " + simulationId);

        SimulationConfig cfg = mapper.convertValue(defaults, SimulationConfig.class);
        File f = new File(defaults.getConfigFile());
        if (f.exists()) {
            mapper.readerForUpdating(cfg).readValue(f);
            log.info("Loaded overrides from {}", f.getAbsolutePath());
        }
        if (body != null && !body.isNull() && body.size() > 0) {
            mapper.readerForUpdating(cfg).readValue(body);
        }

        this.active = cfg;
        this.simulationId = UUID.randomUUID().toString().substring(0, 8);
        this.sent.set(0); this.ok.set(0); this.failed.set(0);
        this.state = "RUNNING";

        final String id = simulationId;
        Thread t = new Thread(() -> run(id, cfg), "sim-orchestrator-" + id);
        t.setDaemon(true);
        t.start();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("simulationId", id);
        res.put("state", state);
        res.put("effectiveConfig", cfg);
        return res;
    }

    private void run(String id, SimulationConfig cfg) {
        log.info("Simulation {} started: {} requests, concurrency {}", id, cfg.getRequests(), cfg.getConcurrency());
        ExecutorService pool = Executors.newFixedThreadPool(cfg.getConcurrency());
        for (int i = 0; i < cfg.getRequests(); i++) {
            final int n = i;
            pool.submit(() -> callWallet(id, n, cfg));
        }
        pool.shutdown();
        try {
            pool.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        state = "COMPLETED";
        log.info("Simulation {} finished: ok={} failed={} retainedKb={}", id, ok.get(), failed.get(), memoryHog.retainedKb());
    }

    private void callWallet(String id, int n, SimulationConfig cfg) {
        try {
            long row = cfg.getRows().get(n % cfg.getRows().size());   // alternate between the 2 rows
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("simulationId", id);
            payload.put("requestNo", n);
            payload.put("totalRequests", cfg.getRequests());
            payload.put("rowId", row);
            payload.put("amount", cfg.getAmount());
            payload.put("holdMs", cfg.getHoldMs());
            payload.put("cpuBurnMs", cfg.getCpuBurnMs());

            HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.getWalletBaseUrl() + "/wallet/debit"))
                    .timeout(Duration.ofMillis(cfg.getWalletTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            sent.incrementAndGet();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 == 2) ok.incrementAndGet(); else failed.incrementAndGet();

            // after EVERY wallet response, retain memory -> heap fills -> OOM incident
            memoryHog.allocate(cfg.getMemoryPerResponseKb());
        } catch (Exception e) {
            failed.incrementAndGet();
            log.warn("wallet call {} failed: {}", n, e.toString());
        }
    }

    public Map<String, Object> status() {
        Runtime rt = Runtime.getRuntime();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("simulationId", simulationId);
        m.put("state", state);
        m.put("sent", sent.get());
        m.put("ok", ok.get());
        m.put("failed", failed.get());
        m.put("retainedMemoryKb", memoryHog.retainedKb());
        m.put("oomCount", memoryHog.oomCount());
        m.put("heapUsedMb", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
        m.put("heapMaxMb", rt.maxMemory() / 1024 / 1024);
        return m;
    }

    public void reset() {
        memoryHog.reset();
        if (!"RUNNING".equals(state)) state = "IDLE";
    }
}
