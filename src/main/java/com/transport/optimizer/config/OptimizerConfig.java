package com.transport.optimizer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "optimizer")
public class OptimizerConfig {

    private int maxLocations = 50;
    private int maxVehicles = 20;
    private double defaultVehicleCapacity = 1000.0;
    private int timeWindowTolerance = 300;
    private String algorithm = "nearest-neighbor";
    private int twoOptIterations = 100;
}
