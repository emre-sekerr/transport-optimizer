package com.transport.optimizer.dto;

import com.transport.optimizer.model.Location;
import com.transport.optimizer.model.Route;
import com.transport.optimizer.model.Vehicle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class OptimizationRequest {

    @NotEmpty(message = "At least one location is required")
    @Valid
    private List<Location> locations;

    @NotEmpty(message = "At least one vehicle is required")
    @Valid
    private List<Vehicle> vehicles;

    private OptimizationObjective objective = OptimizationObjective.MINIMIZE_DISTANCE;

    private String profile = "driving-car";

    private boolean useTimeWindows = false;

    private boolean useCapacityConstraints = true;

    enum OptimizationObjective {
        MINIMIZE_DISTANCE,
        MINIMIZE_DURATION,
        MINIMIZE_COST,
        MINIMIZE_VEHICLES
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class MatrixRequest {

    @NotEmpty(message = "At least two locations are required")
    @Valid
    private List<Location> locations;

    private String profile = "driving-car";
    private List<String> metrics = List.of("distance", "duration");
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RouteRequest {

    @NotNull
    @Valid
    private Location origin;

    @NotNull
    @Valid
    private Location destination;

    private List<@Valid Location> waypoints;
    private String profile = "driving-car";
    private boolean includeGeometry = true;
    private boolean includeInstructions = true;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class OptimizationResponse {

    private String requestId;
    private LocalDateTime optimizedAt;

    private List<Route> routes;
    private List<Location> unassignedLocations;

    private OptimizationSummary summary;

    private String algorithm;

    private long computationTimeMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class OptimizationSummary {
        private int totalRoutes;
        private int totalStops;
        private double totalDistance;    
        private double totalDuration;   
        private double totalCost;
        private double totalLoad;
        private int unassignedCount;
        private double averageRouteLength;
        private double averageRouteDuration;
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class MatrixResponse {

    private List<Location> locations;
    private double[][] distances;
    private double[][] durations;

    private String profile;
    private LocalDateTime createdAt;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class GeocodingResponse {

    private String query;
    private List<GeocodingResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class GeocodingResult {
        private String name;
        private String label;
        private double latitude;
        private double longitude;
        private double confidence;
        private String country;
        private String region;
        private String locality;
    }
}


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private LocalDateTime timestamp;
    private Map<String, String> fieldErrors;

}