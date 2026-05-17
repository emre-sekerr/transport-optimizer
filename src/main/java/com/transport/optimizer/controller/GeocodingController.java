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
@RequestMapping("/v1/geocode")
@RequiredArgsConstructor
@Tag(name = "Geocoding", description = "Address to coordinate conversion endpoints")
public class GeocodingController {

    private final OptimizationService optimizationService;

    @PostMapping("/search")
    @Operation(summary = "Adres ara", description = "Performs text-based address search and returns coordinates")
    public ResponseEntity<TransportDtos.GeocodingResponse> geocode(
            @Valid @RequestBody TransportDtos.GeocodingRequest request) {
        log.info("POST /v1/geocode/search - '{}'", request.getQuery());
        return ResponseEntity.ok(optimizationService.geocode(request));
    }

    @GetMapping("/search")
    @Operation(summary = "Search address (GET)", description = "Performs address search using a query string")
    public ResponseEntity<TransportDtos.GeocodingResponse> geocodeGet(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "TR") String countryCode) {

        TransportDtos.GeocodingRequest request = TransportDtos.GeocodingRequest.builder()
                .query(query)
                .limit(limit)
                .countryCode(countryCode)
                .build();

        return ResponseEntity.ok(optimizationService.geocode(request));
    }
}
