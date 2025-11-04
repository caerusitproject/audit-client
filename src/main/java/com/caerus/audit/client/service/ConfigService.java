package com.caerus.audit.client.service;


import com.caerus.audit.client.model.ServerAppSettingsDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConfigService {
    private final Logger log = LoggerFactory.getLogger(ConfigService.class);
    private final String serverBase;
    private final String clientId;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile ServerAppSettingsDto cached;
    private final Duration fetchInterval = Duration.ofSeconds(30);

    public ConfigService(String serverBase, String clientId) {
        this.serverBase = serverBase;
        this.clientId = clientId;
    }

    public void start(){
        fetchNow();
        scheduler.scheduleAtFixedRate(this::fetchNow, 30, 30, TimeUnit.SECONDS);
    }

    public void stop(){
        scheduler.shutdownNow();
    }

    public ServerAppSettingsDto getLatest(){
        return cached;
    }

    private void fetchNow(){
        try{
            String url = serverBase + "/api/v1/settings/latest";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if(resp.statusCode() == 200){
                ServerAppSettingsDto dto = mapper.readValue(resp.body(), ServerAppSettingsDto.class);
                cached = dto;
                log.info("onfig refreshed successfully from {}", url);
            } else{
                log.error("Config fetch failed: HTTP {}", resp.statusCode());
            }
        } catch (HttpTimeoutException e){
            log.warn("Config fetch timed out: {}", e.getMessage());
        } catch (ConnectException e){
            log.warn("Cannot connect to server at {}: {}", serverBase, e.getMessage());
        } catch (Exception e) {
            log.warn("Error fetching config: {}", e.getMessage());
        }
    }
}
