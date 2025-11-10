package biz.ugur.busroutebackend.shared.infrastructure.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class StaticFileController {

    private static final String BASE_PATH = "./app/data";

    @GetMapping("/avatars/**")
    public Mono<ResponseEntity<Resource>> serveAvatar(ServerWebExchange exchange) {
        String requestPath = extractPath(exchange, "/api/v1/avatars/");
        log.debug("Serving avatar request: {}", requestPath);

        return serveFile("avatars", requestPath);
    }

    @GetMapping("/banners/**")
    public Mono<ResponseEntity<Resource>> serveBanner(ServerWebExchange exchange) {
        String requestPath = extractPath(exchange, "/api/v1/banners/");
        log.debug("Serving banner request: {}", requestPath);

        return serveFile("banners", requestPath);
    }

    private Mono<ResponseEntity<Resource>> serveFile(String directory, String requestPath) {
        return Mono.fromCallable(() -> {
            Path filePath = Paths.get(BASE_PATH, directory, requestPath).normalize();

            Path basePath = Paths.get(BASE_PATH, directory).toAbsolutePath().normalize();
            Path resolvedPath = filePath.toAbsolutePath().normalize();

            if (!resolvedPath.startsWith(basePath)) {
                log.warn("Attempted path traversal attack: {}", requestPath);
                return ResponseEntity.notFound().<Resource>build();
            }

            log.debug("Serving file from: {}", resolvedPath);

            if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
                log.warn("File not found: {}", resolvedPath);
                return ResponseEntity.notFound().<Resource>build();
            }

            Resource resource = new FileSystemResource(resolvedPath);
            String contentType = determineContentType(resolvedPath);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(resource);
        }).onErrorResume(e -> {
            log.error("Error serving file from {}: {}", directory, e.getMessage(), e);
            ResponseEntity<Resource> notFound = ResponseEntity.notFound().build();
            return Mono.just(notFound);
        });
    }

    private String extractPath(ServerWebExchange exchange, String prefix) {
        String fullPath = exchange.getRequest().getPath().value();

        if (fullPath.startsWith(prefix)) {
            return fullPath.substring(prefix.length());
        }

        return fullPath;
    }

    private String determineContentType(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else if (fileName.endsWith(".webp")) {
            return "image/webp";
        } else if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        }

        return "application/octet-stream";
    }
}
