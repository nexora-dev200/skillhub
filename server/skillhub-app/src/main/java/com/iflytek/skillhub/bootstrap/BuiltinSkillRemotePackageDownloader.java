package com.iflytek.skillhub.bootstrap;

import com.iflytek.skillhub.config.SkillPublishProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class BuiltinSkillRemotePackageDownloader {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    static final String ALLOWED_HOST = "bjcdn.openstorage.cn";

    private static final Logger log = LoggerFactory.getLogger(BuiltinSkillRemotePackageDownloader.class);
    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    private final long maxPackageSize;
    private final HttpClient httpClient;

    @Autowired
    public BuiltinSkillRemotePackageDownloader(SkillPublishProperties properties) {
        this(
                properties,
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build()
        );
    }

    BuiltinSkillRemotePackageDownloader(SkillPublishProperties properties, HttpClient httpClient) {
        this.maxPackageSize = properties.getMaxPackageSize();
        this.httpClient = httpClient;
    }

    public Optional<byte[]> download(URI uri) {
        if (!isAllowedUrl(uri)) {
            log.warn("Skipping built-in skill package download because URL is not allowed: {}", safeUrl(uri));
            return Optional.empty();
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                log.warn("Failed to download built-in skill package from {}: HTTP {}",
                        safeUrl(uri),
                        response.statusCode());
                return Optional.empty();
            }
            try (InputStream body = response.body()) {
                return readBounded(body);
            }
        } catch (IOException ex) {
            log.warn("Failed to download built-in skill package from {}: {}", safeUrl(uri), ex.getMessage());
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while downloading built-in skill package from {}", safeUrl(uri));
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("Failed to download built-in skill package from {}: {}", safeUrl(uri), ex.getMessage());
            return Optional.empty();
        }
    }

    HttpClient httpClient() {
        return httpClient;
    }

    static boolean isAllowedUrl(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        if (uri.getRawUserInfo() != null) {
            return false;
        }
        int port = uri.getPort();
        if (port != -1 && port != 443) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (isDisallowedHostLiteral(normalizedHost)) {
            return false;
        }
        return normalizedHost.equals(ALLOWED_HOST) || normalizedHost.endsWith("." + ALLOWED_HOST);
    }

    private Optional<byte[]> readBounded(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long totalRead = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            totalRead += read;
            if (totalRead > maxPackageSize) {
                log.warn("Built-in skill package download exceeded max package size: {} bytes (max: {})",
                        totalRead,
                        maxPackageSize);
                return Optional.empty();
            }
            outputStream.write(buffer, 0, read);
        }
        return Optional.of(outputStream.toByteArray());
    }

    private static boolean isDisallowedHostLiteral(String host) {
        return "localhost".equals(host)
                || IPV4_LITERAL.matcher(host).matches()
                || host.contains(":");
    }

    private static String safeUrl(URI uri) {
        if (uri == null) {
            return "<null>";
        }
        String host = uri.getHost();
        String path = uri.getRawPath();
        return (host == null ? "<unknown-host>" : host) + (path == null ? "" : path);
    }
}
