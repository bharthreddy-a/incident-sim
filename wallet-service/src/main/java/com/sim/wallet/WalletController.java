package com.sim.wallet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/wallet")
public class WalletController {
    private static final Logger log = LoggerFactory.getLogger(WalletController.class);
    private final WalletService service;

    public WalletController(WalletService service) {
        this.service = service;
    }

    @PostMapping("/debit")
    public Map<String, Object> debit(@RequestBody DebitRequest req) {
        service.debit(req);
        return Map.of("status", "OK", "requestNo", req.requestNo(), "rowId", req.rowId());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> fail(Exception e) {
        log.error("debit failed: {}", e.toString());   // e.g. "Lock wait timeout exceeded" / connection not available
        return ResponseEntity.status(500).body(Map.of("status", "FAILED", "error", String.valueOf(e.getMessage())));
    }
}
