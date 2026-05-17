package com.transport.optimizer.controller;

import com.transport.optimizer.dto.TransportDtos;
import com.transport.optimizer.service.OptimizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/optimize")
@RequiredArgsConstructor
@Tag(name = "Optimization", description = "Vehicle route optimization endpoints")
public class OptimizationController {

    private final OptimizationService optimizationService;

    @PostMapping
    @Operation(
            summary = "Optimize vehicle routes",
            description = "Performs ORS-based VRP optimization for the given vehicles and locations"
    )
    public ResponseEntity<TransportDtos.OptimizationResponse> optimize(
            @Valid @RequestBody TransportDtos.OptimizationRequest request) {
        log.info("POST /v1/optimize - {} location, {} vehicle",
                request.getLocations().size(), request.getVehicles().size());

        TransportDtos.OptimizationResponse response = optimizationService.optimize(request);
        return ResponseEntity.ok(response);
    }
}
