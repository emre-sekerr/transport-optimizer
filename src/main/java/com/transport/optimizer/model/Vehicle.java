package com.transport.optimizer.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vehicle {

    private String id;

    @NotBlank(message = "Vehicle name cannot be empty")
    private String name;

    @Min(value = 0, message = "Capacity must be greater than 0")
    private double capacity = 1000.0;

    private Location startLocation;
    private Location endLocation;
    private Long shiftStart;
    private Long shiftEnd;
    private double costPerKm = 1.0;
    private double fixedCost = 0.0;
    private VehicleType type = VehicleType.TRUCK;
    private String orsProfile = "driving-hgv"; 
    private List<String> skills;

    public enum VehicleType {
        CAR,
        TRUCK,
        VAN,
        MOTORCYCLE,
        BICYCLE
    }

}