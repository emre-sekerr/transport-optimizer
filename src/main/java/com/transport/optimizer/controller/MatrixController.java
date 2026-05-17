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
@RequestMapping("/v1/matrix")
@RequiredArgsConstructor
@Tag(name = "Distance Matrix", description = "Calculates distance/duration matrix between location groups")
public class MatrixController {

    private final OptimizationService optimizationService;

    @PostMapping
    @Operation(
            summary = "Calculate distance and duration matrix",
            description = "Returns all distance/duration values between N locations using the ORS Matrix API"
    )
    public ResponseEntity<TransportDtos.MatrixResponse> calculateMatrix(
            @Valid @RequestBody TransportDtos.MatrixRequest request) {
        log.info("POST /v1/matrix - {} location", request.getLocations().size());
        return ResponseEntity.ok(optimizationService.calculateMatrix(request));
    }
}
