package com.transport.optimizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.transport.optimizer.config.OrsConfig;
import com.transport.optimizer.dto.TransportDtos;
import com.transport.optimizer.exception.OrsApiException;
import com.transport.optimizer.model.Location;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrsApiService {

    private final OkHttpClient httpClient;
    private final OrsConfig.OrsProperties orsProperties;
    private final ObjectMapper objectMapper;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Cacheable(value = "distanceMatrix", key = "#profile + '-' + #locations.hashCode()")
    public TransportDtos.MatrixResponse getDistanceMatrix(List<Location> locations, String profile) {
        log.info("Calling ORS Matrix API: {} locations, profile: {}", locations.size(), profile);

        String url = orsProperties.getBaseUrl() + "/v2/matrix/" + profile;

        ObjectNode requestBody = objectMapper.createObjectNode();
        ArrayNode coordinates = objectMapper.createArrayNode();

        for (Location location : locations) {
            ArrayNode coord = objectMapper.createArrayNode();
            coord.add(location.getLongitude());
            coord.add(location.getLatitude());
            coordinates.add(coord);
        }

        requestBody.set("locations", coordinates);
        ArrayNode metrics = objectMapper.createArrayNode();
        metrics.add("distance");
        metrics.add("duration");
        requestBody.set("metrics", metrics);
        requestBody.put("resolve_locations", true);
        requestBody.put("units", "m");

        try {
            String responseBody = executePost(url, requestBody.toString());
            JsonNode response = objectMapper.readTree(responseBody);

            double[][] distances = parseMatrix(response.get("distances"));
            double[][] durations = parseMatrix(response.get("durations"));

            return TransportDtos.MatrixResponse.builder()
                    .locations(locations)
                    .distances(distances)
                    .durations(durations)
                    .profile(profile)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

        } catch (IOException e) {
            log.error("ORS Matrix API error: {}", e.getMessage());
            throw new OrsApiException("Distance matrix could not be calculated: " + e.getMessage(), e);
        }
    }

    public TransportDtos.RouteResponse getRoute(Location origin, Location destination,
                                                  List<Location> waypoints, String profile,
                                                  boolean includeGeometry, boolean includeInstructions) {
        log.info("Calling ORS Directions API: {} -> {}", origin.getName(), destination.getName());

        String url = orsProperties.getBaseUrl() + "/v2/directions/" + profile;

        List<double[]> allCoords = new ArrayList<>();
        allCoords.add(origin.toOrsCoordinate());

        if (waypoints != null) {
            waypoints.forEach(wp -> allCoords.add(wp.toOrsCoordinate()));
        }

        allCoords.add(destination.toOrsCoordinate());

        // Request body
        ObjectNode requestBody = objectMapper.createObjectNode();
        ArrayNode coordinates = objectMapper.createArrayNode();

        for (double[] coord : allCoords) {
            ArrayNode c = objectMapper.createArrayNode();
            c.add(coord[0]);
            c.add(coord[1]);
            coordinates.add(c);
        }

        requestBody.set("coordinates", coordinates);
        requestBody.put("instructions", includeInstructions);
        requestBody.put("geometry", includeGeometry);
        requestBody.put("units", "m");
        requestBody.put("language", "en");

        if (includeGeometry) {
            requestBody.put("geometry_simplify", false);
        }

        try {
            String responseBody = executePost(url, requestBody.toString());
            JsonNode response = objectMapper.readTree(responseBody);

            return parseRouteResponse(response, origin, destination, waypoints, profile);

        } catch (IOException e) {
            log.error("ORS Directions API error: {}", e.getMessage());
            throw new OrsApiException("Route could not be calculated: " + e.getMessage(), e);
        }
    }

    public List<double[]> getRouteGeometry(List<Location> stops, String profile) {
        if (stops.size() < 2) {
            return new ArrayList<>();
        }

        Location origin = stops.get(0);
        Location destination = stops.get(stops.size() - 1);
        List<Location> waypoints = stops.size() > 2 ? stops.subList(1, stops.size() - 1) : null;

        TransportDtos.RouteResponse route = getRoute(origin, destination, waypoints, profile, true, false);
        return route.getGeometry();
    }

    @Cacheable(value = "geocoding", key = "#query + '-' + #countryCode")
    public TransportDtos.GeocodingResponse geocode(String query, String countryCode, int limit) {
        log.info("Calling ORS Geocoding API: {}", query);

        String url = orsProperties.getBaseUrl() + "/geocode/search"
                + "?api_key=" + orsProperties.getKey()
                + "&text=" + encodeUrl(query)
                + "&size=" + limit
                + "&boundary.country=" + countryCode;

        try {
            String responseBody = executeGet(url);
            JsonNode response = objectMapper.readTree(responseBody);

            List<TransportDtos.GeocodingResponse.GeocodingResult> results = new ArrayList<>();
            JsonNode features = response.get("features");

            if (features != null && features.isArray()) {
                for (JsonNode feature : features) {
                    JsonNode props = feature.get("properties");
                    JsonNode geometry = feature.get("geometry");
                    JsonNode coords = geometry.get("coordinates");

                    results.add(TransportDtos.GeocodingResponse.GeocodingResult.builder()
                            .name(getTextNode(props, "name"))
                            .label(getTextNode(props, "label"))
                            .longitude(coords.get(0).asDouble())
                            .latitude(coords.get(1).asDouble())
                            .confidence(getDoubleNode(props, "confidence"))
                            .country(getTextNode(props, "country"))
                            .region(getTextNode(props, "region"))
                            .locality(getTextNode(props, "locality"))
                            .build());
                }
            }

            return TransportDtos.GeocodingResponse.builder()
                    .query(query)
                    .results(results)
                    .build();

        } catch (IOException e) {
            log.error("ORS Geocoding API error: {}", e.getMessage());
            throw new OrsApiException("Geocoding failed: " + e.getMessage(), e);
        }
    }

    @Cacheable(value = "reverseGeocode", key = "#latitude + '-' + #longitude")
    public String reverseGeocode(double latitude, double longitude) {
        log.debug("ORS Reverse Geocoding: {}, {}", latitude, longitude);

        String url = orsProperties.getBaseUrl() + "/geocode/reverse"
                + "?api_key=" + orsProperties.getKey()
                + "&point.lat=" + latitude
                + "&point.lon=" + longitude
                + "&size=1";

        try {
            String responseBody = executeGet(url);
            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode features = response.get("features");

            if (features != null && features.isArray() && features.size() > 0) {
                return features.get(0).get("properties").get("label").asText();
            }
            return String.format("%.4f, %.4f", latitude, longitude);

        } catch (IOException e) {
            log.warn("Reverse geocoding failed: {}", e.getMessage());
            return String.format("%.4f, %.4f", latitude, longitude);
        }
    }

    private String executePost(String url, String jsonBody) throws IOException {
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", orsProperties.getKey())
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json, application/geo+json")
                .post(body)
                .build();

        return executeRequest(request);
    }

    private String executeGet(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .get()
                .build();

        return executeRequest(request);
    }

    private String executeRequest(Request request) throws IOException {
        int retries = 0;
        IOException lastException = null;

        while (retries < orsProperties.getMaxRetries()) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("ORS API error - HTTP {}: {}", response.code(), errorBody);
                    throw new OrsApiException("ORS API error - HTTP " + response.code() + ": " + errorBody);
                }

                assert response.body() != null;
                return response.body().string();

            } catch (IOException e) {
                lastException = e;
                retries++;
                log.warn("ORS API request failed, attempt {}/{}: {}", retries, orsProperties.getMaxRetries(), e.getMessage());

                if (retries < orsProperties.getMaxRetries()) {
                    try {
                        Thread.sleep(1000L * retries); 
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new OrsApiException("Request was interrupted");
                    }
                }
            }
        }

        throw new OrsApiException("Maximum retry count reached", lastException);
    }

    private double[][] parseMatrix(JsonNode matrixNode) {
        if (matrixNode == null || !matrixNode.isArray()) {
            return new double[0][0];
        }

        int size = matrixNode.size();
        double[][] matrix = new double[size][size];

        for (int i = 0; i < size; i++) {
            JsonNode row = matrixNode.get(i);
            for (int j = 0; j < row.size(); j++) {
                JsonNode cell = row.get(j);
                matrix[i][j] = cell.isNull() ? Double.MAX_VALUE : cell.asDouble();
            }
        }

        return matrix;
    }

    private TransportDtos.RouteResponse parseRouteResponse(JsonNode response, Location origin,
                                                             Location destination,
                                                             List<Location> waypoints,
                                                             String profile) {
        JsonNode routes = response.get("routes");
        if (routes == null || !routes.isArray() || routes.isEmpty()) {
            throw new OrsApiException("No valid route was received from ORS");
        }

        JsonNode route = routes.get(0);
        JsonNode summary = route.get("summary");

        double distance = summary.get("distance").asDouble();
        double duration = summary.get("duration").asDouble();

        List<double[]> geometry = new ArrayList<>();
        JsonNode geometryNode = route.get("geometry");
        if (geometryNode != null) {
            JsonNode coords = geometryNode.get("coordinates");
            if (coords != null && coords.isArray()) {
                for (JsonNode coord : coords) {
                    geometry.add(new double[]{coord.get(0).asDouble(), coord.get(1).asDouble()});
                }
            }
        }

        List<TransportDtos.RouteResponse.RouteInstruction> instructions = new ArrayList<>();
        JsonNode segments = route.get("segments");
        if (segments != null && segments.isArray()) {
            for (JsonNode segment : segments) {
                JsonNode steps = segment.get("steps");
                if (steps != null && steps.isArray()) {
                    for (JsonNode step : steps) {
                        List<double[]> stepGeom = geometry;
                        JsonNode wayPts = step.get("way_points");
                        double[] stepLoc = null;
                        if (wayPts != null && wayPts.isArray() && wayPts.size() > 0) {
                            int geomIdx = wayPts.get(0).asInt();
                            if (geomIdx < geometry.size()) {
                                stepLoc = geometry.get(geomIdx);
                            }
                        }

                        instructions.add(TransportDtos.RouteResponse.RouteInstruction.builder()
                                .instruction(getTextNode(step, "instruction"))
                                .type(getTextNode(step, "type"))
                                .distance(getDoubleNode(step, "distance"))
                                .duration(getDoubleNode(step, "duration"))
                                .location(stepLoc)
                                .streetName(getTextNode(step, "name"))
                                .build());
                    }
                }
            }
        }

        return TransportDtos.RouteResponse.builder()
                .origin(origin)
                .destination(destination)
                .waypoints(waypoints)
                .distanceMeters(distance)
                .durationSeconds(duration)
                .distanceKm(distance / 1000.0)
                .durationMinutes(duration / 60.0)
                .geometry(geometry)
                .instructions(instructions)
                .profile(profile)
                .build();
    }

    private String getTextNode(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText();
    }

    private double getDoubleNode(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return 0.0;
        }
        return node.get(field).asDouble();
    }

    private String encodeUrl(String text) {
        return java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8);
    }
}
