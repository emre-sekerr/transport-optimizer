package com.transport.optimizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Route {

    private String vehicleId;
    private String vehicleName;
    private List<Location> stops;
    private List<double[]> geometry;
    private double totalDistance;
    private double totalDuration;
    private double totalLoad;
    private double totalCost;
    private List<RouteStep> steps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteStep {
        private int stepIndex;
        private String locationId;
        private String locationName;
        private double latitude;
        private double longitude;
        private String instruction;
        private double distanceFromPrevious;  
        private double durationFromPrevious; 
        private long arrivalTime;             
        private long departureTime;           
        private double loadAtStep;
    }

}