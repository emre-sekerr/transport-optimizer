package com.transport.optimizer.controller;

import com.transport.optimizer.config.OrsConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "System and ORS connection status")
public class HealthController {

    private final OkHttpClient httpClient;
    private final OrsConfig.OrsProperties orsProperties;

    @GetMapping
    @Operation(summary = "System status", description = "Checks API and ORS connectivity")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("timestamp", LocalDateTime.now().toString());
        status.put("service", "Transport Optimizer");

        // ORS bağlantı kontrolü
        boolean orsConnected = checkOrsConnection();
        status.put("ors", Map.of(
                "connected", orsConnected,
                "baseUrl", orsProperties.getBaseUrl(),
                "apiKeyConfigured", orsProperties.getKey() != null
                        && !orsProperties.getKey().isBlank()
                        && !orsProperties.getKey().equals("YOUR_ORS_API_KEY_HERE")
        ));

        return ResponseEntity.ok(status);
    }

    private boolean checkOrsConnection() {
        try {
            String url = orsProperties.getBaseUrl() + "/geocode/search"
                    + "?api_key=" + orsProperties.getKey()
                    + "&text=test&size=1";

            Request request = new Request.Builder().url(url).get().build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.code() != 401 && response.code() != 403;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
