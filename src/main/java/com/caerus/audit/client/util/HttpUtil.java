package com.caerus.audit.client.util;

import com.caerus.audit.client.service.ConfigService;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class HttpUtil {
    private static final Logger log = LoggerFactory.getLogger(HttpUtil.class);

    private final String serverBaseUrl;
    private final String clientId;

    public HttpUtil(String serverBaseUrl, String clientId) {
        this.serverBaseUrl = serverBaseUrl;
        this.clientId = clientId;
    }

    /**
     * Uploads a file with retry-safe handling.
     * @param file Path to file for upload.
     * @return true if upload succeeds (2xx), false otherwise.
     */
    public boolean uploadFile(Path file, String uploadId) {
        String endpoint = serverBaseUrl + "/api/v1/upload";
        int maxRetries = 3;
        long retryDelaySec = 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpPost post = new HttpPost(endpoint);
                post.addHeader("Client-Id", clientId);
                post.addHeader("X-Upload-Id", uploadId);

                HttpEntity entity = MultipartEntityBuilder.create()
                        .addBinaryBody("file", file.toFile(), ContentType.APPLICATION_OCTET_STREAM, file.getFileName().toString())
                        .build();

                post.setEntity(entity);

                try (CloseableHttpResponse response = client.execute(post)) {
                    int statusCode = response.getCode();
                    String body = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";
                    log.info("Upload response [{}]: {}", statusCode, body);

                    if (statusCode >= 200 && statusCode < 300) {
                        return true;
                    }

                    log.warn("Upload failed (status {}), attempt {}/{}", statusCode, attempt, maxRetries);
                }
            } catch (Exception e) {
                log.error("Upload error for {} (attempt {}/{}): {}", file.getFileName(), attempt, maxRetries, e.getMessage());
            }

            // Retry delay
            try {
                TimeUnit.SECONDS.sleep(retryDelaySec);
                retryDelaySec *= 2;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Upload retry interrupted");
                return false;
            }
        }

        log.error("File upload failed after {} attempts: {}", maxRetries, file);
        return false;
    }
}
