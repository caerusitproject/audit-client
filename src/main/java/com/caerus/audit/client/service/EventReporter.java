package com.caerus.audit.client.service;

import com.caerus.audit.client.model.ErrorLogRequest;
import com.caerus.audit.client.model.EventLogRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventReporter {
  private final Logger log = LoggerFactory.getLogger(EventReporter.class);
  private final String serverBaseUrl;
  private final String clientId;
  private final String ipAddress;
  private final ObjectMapper mapper;

  public EventReporter(String serverBaseUrl, String clientId, String ipAddress) {
    this.serverBaseUrl = serverBaseUrl;
    this.clientId = clientId;
    this.ipAddress = ipAddress;

    this.mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public void logEvent(byte eventTypeId, String eventDesc) {
    try {
      EventLogRequest request = new EventLogRequest();
      request.setEventTypeId(eventTypeId);
      request.setEventDesc(eventDesc);
      request.setEventSource(clientId);
      request.setEventSrcIPAddr(ipAddress);
      request.setEventDTime(Instant.now());

      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(serverBaseUrl + "/api/v1/logs"))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request)))
              .build();

      HttpResponse<String> response =
          HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        log.warn("Failed to report event (HTTP {}): {}", response.statusCode(), response.body());
      }
    } catch (Exception e) {
      log.error("Error reporting failure", e);
    }
  }

  public void logError(byte errorTypeId, String errorDesc) {
    try {
      ErrorLogRequest request = new ErrorLogRequest();
      request.setErrorTypeId(errorTypeId);
      request.setErrorDesc(errorDesc);
      request.setErrorSource(clientId);
      request.setErrorSrcIPAddr(ipAddress);
      request.setErrorDTime(Instant.now());

      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(serverBaseUrl + "/api/v1/logs/error"))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request)))
              .build();

      HttpResponse<String> response =
          HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        log.warn("Failed to report error (HTTP {}): {}", response.statusCode(), response.body());
      }
    } catch (Exception e) {
      log.error("Error reporting failure", e);
    }
  }
}
