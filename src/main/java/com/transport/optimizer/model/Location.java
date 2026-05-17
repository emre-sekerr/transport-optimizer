package com.transport.optimizer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Location {

    private String id;

    @NotBlank(message = "Location name cannot be empty")
    private String name;

    @NotNull(message = "Latitude value is required")
    @Min(value = -90, message = "Latitude must be between -90 and 90")
    @Max(value = 90, message = "Latitude must be between -90 and 90")
    private Double latitude;

    @NotNull(message = "Longitude value is required")
    @Min(value = -180, message = "Longitude must be between -180 and 180")
    @Max(value = 180, message = "Longitude must be between -180 and 180")
    private Double longitude;
    private String address;
    private double demand = 0.0;
    private Long timeWindowStart;
    private Long timeWindowEnd;
    private int serviceDuration = 0;
    private LocationType type = LocationType.DELIVERY;

    public enum LocationType {
        DEPOT,      
        PICKUP,     
        DELIVERY    
    }

    public double[] toOrsCoordinate() {
        return new double[]{longitude, latitude};
    }

}