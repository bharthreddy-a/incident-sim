package com.sim.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/simulation")
public class SimulationController {
    private final SimulationService service;

    public SimulationController(SimulationService service) {
        this.service = service;
    }

    /** Body is optional. Any field in it overrides yml + local json defaults. */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestBody(required = false) JsonNode body) throws Exception {
        return ResponseEntity.accepted().body(service.start(body));
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return service.status();
    }

    /** frees the retained memory (resolves OOM incident) */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        service.reset();
        return service.status();
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    }
}
