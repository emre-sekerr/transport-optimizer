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
@RequestMapping("/v1/route")
@RequiredArgsConstructor
@Tag(name = "Route", description = "Single route calculation endpoints")
public class RouteController {

    private final OptimizationService optimizationService;

    @PostMapping
    @Operation(
            summary = "Calculate route between two points",
            description = "Returns route, distance, and duration information using the ORS Directions API"
    )
    public ResponseEntity<TransportDtos.RouteResponse> calculateRoute(
            @Valid @RequestBody TransportDtos.RouteRequest request) {
        log.info("POST /v1/route - {} -> {}", request.getOrigin().getName(), request.getDestination().getName());
        return ResponseEntity.ok(optimizationService.calculateRoute(request));
    }
}
