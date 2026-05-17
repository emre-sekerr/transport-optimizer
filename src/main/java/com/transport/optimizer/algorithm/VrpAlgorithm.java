package com.transport.optimizer.algorithm;

import com.transport.optimizer.model.Location;
import com.transport.optimizer.model.Route;
import com.transport.optimizer.model.Vehicle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class VrpAlgorithm {

    public List<Route> solveNearestNeighbor(List<Location> locations,
                                             List<Vehicle> vehicles,
                                             double[][] distanceMatrix,
                                             double[][] durationMatrix,
                                             boolean useCapacity) {
        log.info("Nearest Neighbor algorithm started: {} locations, {} vehicles", locations.size(), vehicles.size());

      
        List<Location> depots = locations.stream()
                .filter(l -> l.getType() == Location.LocationType.DEPOT)
                .toList();

        List<Location> customers = new ArrayList<>(locations.stream()
                .filter(l -> l.getType() != Location.LocationType.DEPOT)
                .toList());

        if (depots.isEmpty()) {
            throw new IllegalArgumentException("At least one location with type DEPOT is required");
        }

        List<Route> routes = new ArrayList<>();
        Set<String> assignedCustomers = new HashSet<>();

        for (int v = 0; v < vehicles.size(); v++) {
            Vehicle vehicle = vehicles.get(v);

            Location depot = depots.get(v % depots.size());
            if (vehicle.getStartLocation() != null) {
                depot = vehicle.getStartLocation();
            }

            List<Location> routeStops = new ArrayList<>();
            routeStops.add(depot);

            double currentLoad = 0.0;
            Location currentLocation = depot;
            double totalDistance = 0.0;
            double totalDuration = 0.0;

            while (true) {
                Location nearest = findNearestUnassigned(
                        currentLocation, customers, assignedCustomers,
                        distanceMatrix, locations,
                        useCapacity ? vehicle.getCapacity() - currentLoad : Double.MAX_VALUE
                );

                if (nearest == null) break;

                int fromIdx = locations.indexOf(currentLocation);
                int toIdx = locations.indexOf(nearest);

                if (fromIdx >= 0 && toIdx >= 0 && fromIdx < distanceMatrix.length && toIdx < distanceMatrix[0].length) {
                    totalDistance += distanceMatrix[fromIdx][toIdx];
                    totalDuration += durationMatrix[fromIdx][toIdx];
                }

                currentLoad += nearest.getDemand();
                routeStops.add(nearest);
                assignedCustomers.add(nearest.getId());
                currentLocation = nearest;

                if (assignedCustomers.size() >= customers.size()) break;
            }

            if (routeStops.size() > 1) {
                int fromIdx = locations.indexOf(currentLocation);
                int depotIdx = locations.indexOf(depot);

                if (fromIdx >= 0 && depotIdx >= 0 && fromIdx < distanceMatrix.length) {
                    totalDistance += distanceMatrix[fromIdx][depotIdx];
                    totalDuration += durationMatrix[fromIdx][depotIdx];
                }
                routeStops.add(depot); 
            }

            if (routeStops.size() > 2) { 
                Route route = Route.builder()
                        .vehicleId(vehicle.getId())
                        .vehicleName(vehicle.getName())
                        .stops(routeStops)
                        .totalDistance(totalDistance)
                        .totalDuration(totalDuration)
                        .totalLoad(currentLoad)
                        .totalCost(totalDistance / 1000.0 * vehicle.getCostPerKm() + vehicle.getFixedCost())
                        .build();

                routes.add(route);
            }

            if (assignedCustomers.size() >= customers.size()) break;
        }

        log.info("Nearest Neighbor completed: {} routes created, {} customers assigned",
                routes.size(), assignedCustomers.size());
        return routes;
    }

    public Route twoOptImprove(Route route, double[][] distanceMatrix, List<Location> allLocations, int maxIterations) {
        List<Location> stops = new ArrayList<>(route.getStops());
        int n = stops.size();

        if (n < 4) return route; 

        double bestDistance = route.getTotalDistance();
        boolean improved = true;
        int iteration = 0;

        while (improved && iteration < maxIterations) {
            improved = false;
            iteration++;

            for (int i = 1; i < n - 2; i++) {
                for (int j = i + 1; j < n - 1; j++) {
                	
                    double currentDist = getDistance(stops.get(i - 1), stops.get(i), distanceMatrix, allLocations)
                            + getDistance(stops.get(j), stops.get(j + 1), distanceMatrix, allLocations);

                    double newDist = getDistance(stops.get(i - 1), stops.get(j), distanceMatrix, allLocations)
                            + getDistance(stops.get(i), stops.get(j + 1), distanceMatrix, allLocations);

                    if (newDist < currentDist - 1e-6) {
                        reverseSegment(stops, i, j);
                        bestDistance = bestDistance - currentDist + newDist;
                        improved = true;
                    }
                }
            }
        }

        log.debug("2-opt completed: {} iterations, distance improvement: {:.2f}m",
                iteration, route.getTotalDistance() - bestDistance);

        return Route.builder()
                .vehicleId(route.getVehicleId())
                .vehicleName(route.getVehicleName())
                .stops(stops)
                .totalDistance(bestDistance)
                .totalDuration(route.getTotalDuration())
                .totalLoad(route.getTotalLoad())
                .totalCost(route.getTotalCost())
                .geometry(route.getGeometry())
                .build();
    }

    public Route orOptImprove(Route route, double[][] distanceMatrix, List<Location> allLocations) {
        List<Location> stops = new ArrayList<>(route.getStops());
        int n = stops.size();

        if (n < 5) return route;

        double bestDistance = route.getTotalDistance();
        boolean improved = true;

        while (improved) {
            improved = false;

            for (int segLen = 1; segLen <= 2; segLen++) {
                for (int i = 1; i < n - segLen; i++) {
                    for (int j = 1; j < n - 1; j++) {
                        if (j >= i && j <= i + segLen) continue;

                        List<Location> newStops = new ArrayList<>(stops);
                        List<Location> segment = new ArrayList<>(newStops.subList(i, i + segLen));
                        newStops.subList(i, i + segLen).clear();

                        int insertPos = j <= i ? j : j - segLen;
                        if (insertPos >= 0 && insertPos <= newStops.size()) {
                            newStops.addAll(insertPos, segment);

                            double newDistance = calculateTotalDistance(newStops, distanceMatrix, allLocations);

                            if (newDistance < bestDistance - 1e-6) {
                                stops = newStops;
                                bestDistance = newDistance;
                                improved = true;
                            }
                        }
                    }
                }
            }
        }

        return Route.builder()
                .vehicleId(route.getVehicleId())
                .vehicleName(route.getVehicleName())
                .stops(stops)
                .totalDistance(bestDistance)
                .totalDuration(route.getTotalDuration())
                .totalLoad(route.getTotalLoad())
                .totalCost(route.getTotalCost())
                .build();
    }


    private Location findNearestUnassigned(Location current, List<Location> customers,
                                            Set<String> assigned, double[][] distMatrix,
                                            List<Location> allLocations, double remainingCapacity) {
        Location nearest = null;
        double minDist = Double.MAX_VALUE;

        int currentIdx = allLocations.indexOf(current);

        for (Location customer : customers) {
            if (assigned.contains(customer.getId())) continue;
            if (customer.getDemand() > remainingCapacity) continue;

            int customerIdx = allLocations.indexOf(customer);
            double dist = (currentIdx >= 0 && customerIdx >= 0 && currentIdx < distMatrix.length
                    && customerIdx < distMatrix[0].length)
                    ? distMatrix[currentIdx][customerIdx]
                    : haversineDistance(current, customer);

            if (dist < minDist) {
                minDist = dist;
                nearest = customer;
            }
        }

        return nearest;
    }

    private void reverseSegment(List<Location> stops, int i, int j) {
        while (i < j) {
            Location temp = stops.get(i);
            stops.set(i, stops.get(j));
            stops.set(j, temp);
            i++;
            j--;
        }
    }

    private double getDistance(Location a, Location b, double[][] distMatrix, List<Location> allLocations) {
        int idxA = allLocations.indexOf(a);
        int idxB = allLocations.indexOf(b);

        if (idxA >= 0 && idxB >= 0 && idxA < distMatrix.length && idxB < distMatrix[0].length) {
            return distMatrix[idxA][idxB];
        }

        return haversineDistance(a, b);
    }

    private double calculateTotalDistance(List<Location> stops, double[][] distMatrix, List<Location> allLocations) {
        double total = 0.0;
        for (int i = 0; i < stops.size() - 1; i++) {
            total += getDistance(stops.get(i), stops.get(i + 1), distMatrix, allLocations);
        }
        return total;
    }

    public double haversineDistance(Location a, Location b) {
        final double R = 6371000; // Dünya yarıçapı (metre)
        double lat1 = Math.toRadians(a.getLatitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double dLat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());

        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return R * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    public List<Location> getUnassignedLocations(List<Location> allLocations, List<Route> routes) {
        Set<String> assignedIds = new HashSet<>();
        routes.forEach(r -> r.getStops().forEach(s -> assignedIds.add(s.getId())));

        return allLocations.stream()
                .filter(l -> l.getType() != Location.LocationType.DEPOT)
                .filter(l -> !assignedIds.contains(l.getId()))
                .toList();
    }
}
