package com.transport.optimizer.service;

import com.transport.optimizer.algorithm.VrpAlgorithm;
import com.transport.optimizer.config.OptimizerConfig;
import com.transport.optimizer.dto.TransportDtos;
import com.transport.optimizer.exception.OptimizationException;
import com.transport.optimizer.model.Location;
import com.transport.optimizer.model.Route;
import com.transport.optimizer.model.Vehicle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OptimizationService {

    private final OrsApiService orsApiService;
    private final VrpAlgorithm vrpAlgorithm;
    private final OptimizerConfig config;

    public TransportDtos.OptimizationResponse optimize(TransportDtos.OptimizationRequest request) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        log.info("Optimization started [{}]: {} locations, {} vehicles",
                requestId, request.getLocations().size(), request.getVehicles().size());

        validateRequest(request);

        assignIds(request.getLocations(), request.getVehicles());

        log.info("[{}] Fetching distance matrix...", requestId);
        TransportDtos.MatrixResponse matrixResponse;

        try {
            matrixResponse = orsApiService.getDistanceMatrix(request.getLocations(), request.getProfile());
        } catch (Exception e) {
            log.warn("Could not fetch ORS matrix, using Haversine distance: {}", e.getMessage());
            matrixResponse = buildFallbackMatrix(request.getLocations());
        }

        double[][] distances = matrixResponse.getDistances();
        double[][] durations = matrixResponse.getDurations();
        
        log.info("[{}] Applying {} algorithm...", requestId, config.getAlgorithm());
        List<Route> routes = solveVrp(request, distances, durations);

        if (!config.getAlgorithm().equals("two-opt") && routes.size() > 0) {
            log.info("[{}] Applying 2-opt improvement...", requestId);
            routes = routes.stream()
                    .map(r -> vrpAlgorithm.twoOptImprove(r, distances, request.getLocations(), config.getTwoOptIterations()))
                    .collect(Collectors.toList());
        }

        log.info("[{}] Fetching route geometries...", requestId);
        routes = enrichRoutesWithGeometry(routes, request.getProfile());

        routes = calculateRouteSteps(routes, distances, durations, request.getLocations());

        List<Location> unassigned = vrpAlgorithm.getUnassignedLocations(request.getLocations(), routes);

        long computationTime = System.currentTimeMillis() - startTime;
        log.info("[{}] Optimization completed: {} routes, {} unassigned, {}ms",
                requestId, routes.size(), unassigned.size(), computationTime);

        return TransportDtos.OptimizationResponse.builder()
                .requestId(requestId)
                .optimizedAt(LocalDateTime.now())
                .routes(routes)
                .unassignedLocations(unassigned)
                .summary(buildSummary(routes, unassigned))
                .algorithm(config.getAlgorithm())
                .computationTimeMs(computationTime)
                .build();
    }

    public TransportDtos.RouteResponse calculateRoute(TransportDtos.RouteRequest request) {
        log.info("Calculating route: {} -> {}", request.getOrigin().getName(), request.getDestination().getName());

        return orsApiService.getRoute(
                request.getOrigin(),
                request.getDestination(),
                request.getWaypoints(),
                request.getProfile(),
                request.isIncludeGeometry(),
                request.isIncludeInstructions()
        );
    }

    public TransportDtos.MatrixResponse calculateMatrix(TransportDtos.MatrixRequest request) {
        log.info("Calculating distance matrix: {} locations", request.getLocations().size());

        if (request.getLocations().size() > config.getMaxLocations()) {
            throw new OptimizationException(
                    String.format("Maximum number of locations (%d) exceeded", config.getMaxLocations()));
        }

        assignLocationIds(request.getLocations());
        return orsApiService.getDistanceMatrix(request.getLocations(), request.getProfile());
    }

    public TransportDtos.GeocodingResponse geocode(TransportDtos.GeocodingRequest request) {
        return orsApiService.geocode(request.getQuery(), request.getCountryCode(), request.getLimit());
    }

    private void validateRequest(TransportDtos.OptimizationRequest request) {
        if (request.getLocations().size() > config.getMaxLocations()) {
            throw new OptimizationException(
                    String.format("Maximum %d locations are supported", config.getMaxLocations()));
        }

        if (request.getVehicles().size() > config.getMaxVehicles()) {
            throw new OptimizationException(
                    String.format("Maximum %d vehicles are supported", config.getMaxVehicles()));
        }

        boolean hasDepot = request.getLocations().stream()
                .anyMatch(l -> l.getType() == Location.LocationType.DEPOT);

        if (!hasDepot) {
            request.getLocations().get(0).setType(Location.LocationType.DEPOT);
            log.warn("No depot found, first location assigned as depot: {}",
                    request.getLocations().get(0).getName());
        }
    }

    private void assignIds(List<Location> locations, List<Vehicle> vehicles) {
        assignLocationIds(locations);

        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle v = vehicles.get(i);
            if (v.getId() == null || v.getId().isBlank()) {
                v.setId("V" + (i + 1));
            }
            if (v.getName() == null || v.getName().isBlank()) {
                v.setName("Vehicle " + (i + 1));
            }
        }
    }

    private void assignLocationIds(List<Location> locations) {
        for (int i = 0; i < locations.size(); i++) {
            Location l = locations.get(i);
            if (l.getId() == null || l.getId().isBlank()) {
                l.setId("L" + (i + 1));
            }
        }
    }

    private List<Route> solveVrp(TransportDtos.OptimizationRequest request,
                                  double[][] distances, double[][] durations) {
        return switch (config.getAlgorithm()) {
            case "two-opt" -> {
                List<Route> initial = vrpAlgorithm.solveNearestNeighbor(
                        request.getLocations(), request.getVehicles(),
                        distances, durations, request.isUseCapacityConstraints());

                yield initial.stream()
                        .map(r -> vrpAlgorithm.twoOptImprove(
                                r, distances, request.getLocations(), config.getTwoOptIterations()))
                        .collect(Collectors.toList());
            }
            case "or-opt" -> {
                List<Route> initial = vrpAlgorithm.solveNearestNeighbor(
                        request.getLocations(), request.getVehicles(),
                        distances, durations, request.isUseCapacityConstraints());

                yield initial.stream()
                        .map(r -> vrpAlgorithm.orOptImprove(r, distances, request.getLocations()))
                        .collect(Collectors.toList());
            }
            default -> 
                    vrpAlgorithm.solveNearestNeighbor(
                            request.getLocations(), request.getVehicles(),
                            distances, durations, request.isUseCapacityConstraints());
        };
    }

    private List<Route> enrichRoutesWithGeometry(List<Route> routes, String profile) {
        List<Route> enriched = new ArrayList<>();

        for (Route route : routes) {
            try {
                if (route.getStops() != null && route.getStops().size() >= 2) {
                    List<double[]> geometry = orsApiService.getRouteGeometry(route.getStops(), profile);
                    enriched.add(Route.builder()
                            .vehicleId(route.getVehicleId())
                            .vehicleName(route.getVehicleName())
                            .stops(route.getStops())
                            .geometry(geometry)
                            .totalDistance(route.getTotalDistance())
                            .totalDuration(route.getTotalDuration())
                            .totalLoad(route.getTotalLoad())
                            .totalCost(route.getTotalCost())
                            .build());
                } else {
                    enriched.add(route);
                }
            } catch (Exception e) {
                log.warn("Could not fetch geometry for vehicle {}: {}", route.getVehicleId(), e.getMessage());
                enriched.add(route);
            }
        }

        return enriched;
    }

    private List<Route> calculateRouteSteps(List<Route> routes, double[][] distances,
                                             double[][] durations, List<Location> allLocations) {
        List<Route> result = new ArrayList<>();

        for (Route route : routes) {
            List<Route.RouteStep> steps = new ArrayList<>();
            List<Location> stops = route.getStops();
            long currentTime = System.currentTimeMillis() / 1000; // current time (Unix)
            double currentLoad = 0.0;

            for (int i = 0; i < stops.size(); i++) {
                Location loc = stops.get(i);
                double distFromPrev = 0.0;
                double durFromPrev = 0.0;

                if (i > 0) {
                    int prevIdx = allLocations.indexOf(stops.get(i - 1));
                    int currIdx = allLocations.indexOf(loc);

                    if (prevIdx >= 0 && currIdx >= 0 && prevIdx < distances.length) {
                        distFromPrev = distances[prevIdx][currIdx];
                        durFromPrev = durations[prevIdx][currIdx];
                    }
                }

                long arrival = currentTime + (long) durFromPrev;
                currentLoad += loc.getDemand();

                steps.add(Route.RouteStep.builder()
                        .stepIndex(i)
                        .locationId(loc.getId())
                        .locationName(loc.getName())
                        .latitude(loc.getLatitude())
                        .longitude(loc.getLongitude())
                        .distanceFromPrevious(distFromPrev)
                        .durationFromPrevious(durFromPrev)
                        .arrivalTime(arrival)
                        .departureTime(arrival + loc.getServiceDuration())
                        .loadAtStep(currentLoad)
                        .instruction(buildInstruction(i, stops.size(), loc))
                        .build());

                currentTime = arrival + loc.getServiceDuration();
            }

            result.add(Route.builder()
                    .vehicleId(route.getVehicleId())
                    .vehicleName(route.getVehicleName())
                    .stops(route.getStops())
                    .geometry(route.getGeometry())
                    .totalDistance(route.getTotalDistance())
                    .totalDuration(route.getTotalDuration())
                    .totalLoad(route.getTotalLoad())
                    .totalCost(route.getTotalCost())
                    .steps(steps)
                    .build());
        }

        return result;
    }

    private String buildInstruction(int index, int total, Location loc) {
        if (index == 0) return "Start: " + loc.getName();
        if (index == total - 1) return "End (Return to depot): " + loc.getName();
        return (loc.getType() == Location.LocationType.PICKUP ? "Pick up" : "Deliver") + ": " + loc.getName();
    }

    private TransportDtos.OptimizationResponse.OptimizationSummary buildSummary(List<Route> routes,
                                                                                  List<Location> unassigned) {
        double totalDist = routes.stream().mapToDouble(Route::getTotalDistance).sum();
        double totalDur = routes.stream().mapToDouble(Route::getTotalDuration).sum();
        double totalCost = routes.stream().mapToDouble(Route::getTotalCost).sum();
        double totalLoad = routes.stream().mapToDouble(Route::getTotalLoad).sum();
        int totalStops = routes.stream().mapToInt(r -> r.getStops() != null ? r.getStops().size() : 0).sum();

        return TransportDtos.OptimizationResponse.OptimizationSummary.builder()
                .totalRoutes(routes.size())
                .totalStops(totalStops)
                .totalDistanceKm(totalDist / 1000.0)
                .totalDurationMinutes(totalDur / 60.0)
                .totalCost(totalCost)
                .totalLoad(totalLoad)
                .unassignedCount(unassigned.size())
                .averageRouteDistanceKm(routes.isEmpty() ? 0 : totalDist / 1000.0 / routes.size())
                .averageRouteDurationMinutes(routes.isEmpty() ? 0 : totalDur / 60.0 / routes.size())
                .build();
    }

    private TransportDtos.MatrixResponse buildFallbackMatrix(List<Location> locations) {
        int n = locations.size();
        double[][] distances = new double[n][n];
        double[][] durations = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    double dist = vrpAlgorithm.haversineDistance(locations.get(i), locations.get(j));
                    distances[i][j] = dist;
                    durations[i][j] = dist / 13.89; 
                }
            }
        }

        return TransportDtos.MatrixResponse.builder()
                .locations(locations)
                .distances(distances)
                .durations(durations)
                .profile("fallback-haversine")
                .createdAt(LocalDateTime.now())
                .build();
    }
}