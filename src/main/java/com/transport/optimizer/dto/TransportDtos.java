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

public class TransportDtos {
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizationRequest {
        @NotEmpty(message = "At least one location is required")
        @Valid
        private List<Location> locations;

        @NotEmpty(message = "At least one vehicle is required")
        @Valid
        private List<Vehicle> vehicles;

        private String objective = "MINIMIZE_DISTANCE";
        private String profile = "driving-car";
        private boolean useTimeWindows = false;
        private boolean useCapacityConstraints = true;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatrixRequest {
        @NotEmpty
        @Valid
        private List<Location> locations;
        private String profile = "driving-car";
        private List<String> metrics = List.of("distance", "duration");
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteRequest {
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
    public static class GeocodingRequest {
        @NotNull(message = "Search query cannot be empty")
        private String query;
        private int limit = 5;
        private String countryCode = "TR";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizationResponse {
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
        public static class OptimizationSummary {
            private int totalRoutes;
            private int totalStops;
            private double totalDistanceKm;
            private double totalDurationMinutes;
            private double totalCost;
            private double totalLoad;
            private int unassignedCount;
            private double averageRouteDistanceKm;
            private double averageRouteDurationMinutes;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatrixResponse {
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
    public static class RouteResponse {
        private Location origin;
        private Location destination;
        private List<Location> waypoints;
        private double distanceMeters;
        private double durationSeconds;
        private double distanceKm;
        private double durationMinutes;
        private List<double[]> geometry;
        private List<RouteInstruction> instructions;
        private String profile;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class RouteInstruction {
            private String instruction;
            private String type;
            private double distance;
            private double duration;
            private double[] location;
            private String streetName;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeocodingResponse {
        private String query;
        private List<GeocodingResult> results;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class GeocodingResult {
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
    public static class ErrorResponse {
        private int status;
        private String error;
        private String message;
        private LocalDateTime timestamp;
        private Map<String, String> fieldErrors;
    }
}
