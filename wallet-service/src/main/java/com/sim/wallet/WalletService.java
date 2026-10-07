package com.sim.wallet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
    private final JdbcTemplate jdbc;

    public WalletService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 1. UPDATE hot row  -> takes the InnoDB X row lock (waits if another txn holds it => row lock wait)
     * 2. SELECT SLEEP()  -> keeps the txn open, so the lock is held for holdMs
     * 3. commit          -> lock released, next waiter proceeds
     * Only 5 connections exist, so at most 5 txns are in the DB; the rest queue in Hikari / Tomcat.
     */
    @Transactional
    public void debit(DebitRequest r) {
        if (r.cpuBurnMs() > 0) burnCpu(r.cpuBurnMs());

        int rows = jdbc.update(
                "UPDATE wallet_account SET balance = balance - ?, version = version + 1 WHERE id = ?",
                r.amount(), r.rowId());
        if (rows != 1) throw new IllegalStateException("account not found: " + r.rowId());

        if (r.holdMs() > 0) {
            jdbc.queryForObject("SELECT SLEEP(?)", Object.class, r.holdMs() / 1000.0);
        }
        log.debug("debit done sim={} req={} row={}", r.simulationId(), r.requestNo(), r.rowId());
    }

    private void burnCpu(int ms) {
        long end = System.nanoTime() + ms * 1_000_000L;
        double x = 0;
        while (System.nanoTime() < end) {
            x += Math.sqrt(Math.random() * 1000);
        }
        if (x < 0) log.trace("{}", x);   // prevent dead-code elimination
    }
}
