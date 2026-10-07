package com.sim.payment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Retains memory forever (until /simulation/reset) so heap fills up -> OutOfMemoryError. */
@Component
public class MemoryHog {
    private static final Logger log = LoggerFactory.getLogger(MemoryHog.class);
    private final List<byte[]> leak = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong retainedKb = new AtomicLong();
    private final AtomicLong oomCount = new AtomicLong();
    private final Counter oomCounter;

    public MemoryHog(MeterRegistry registry) {
        Gauge.builder("sim.retained.memory.kb", retainedKb, AtomicLong::get)
                .description("Memory deliberately retained by payment simulation").register(registry);
        this.oomCounter = Counter.builder("sim.oom.errors")
                .description("OutOfMemoryErrors caught during simulation").register(registry);
    }

    public void allocate(int kb) {
        try {
            byte[] chunk = new byte[kb * 1024];
            java.util.Arrays.fill(chunk, (byte) 1);   // touch pages so memory is really committed
            leak.add(chunk);
            retainedKb.addAndGet(kb);
        } catch (OutOfMemoryError oom) {
            // logged (and heap dump taken if -XX:+HeapDumpOnOutOfMemoryError) -> incident signal
            oomCount.incrementAndGet();
            oomCounter.increment();
            log.error("OutOfMemoryError: Java heap space while retaining payment response data (retained={} KB)",
                    retainedKb.get(), oom);
        }
    }

    public void reset() {
        leak.clear();
        retainedKb.set(0);
        oomCount.set(0);
        System.gc();
    }

    public long retainedKb() { return retainedKb.get(); }
    public long oomCount()   { return oomCount.get(); }
}
