package biz.ugur.busroutebackend.advertising.infrastructure.startup;

import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

@Component
@Slf4j
public class LegacyEditorialContentDecompressionRunner implements ApplicationRunner {

    private static final String GZIP_BASE64_PREFIX = "H4sI";
    private static final String SELECT_GZIPPED = """
            SELECT id, content FROM ad_placements
            WHERE kind = 'EDITORIAL'
              AND content IS NOT NULL
              AND content LIKE 'H4sI%'
            """;
    private static final String UPDATE_DECOMPRESSED = """
            UPDATE ad_placements SET content = :content, updated_at = NOW() WHERE id = :id
            """;

    private final DatabaseClient databaseClient;

    public LegacyEditorialContentDecompressionRunner(ConnectionFactory connectionFactory) {
        this.databaseClient = DatabaseClient.create(connectionFactory);
    }

    @Override
    public void run(ApplicationArguments args) {
        long started = System.currentTimeMillis();
        Long processed = databaseClient.sql(SELECT_GZIPPED)
                .map((row, meta) -> new GzippedRow(row.get("id", String.class), row.get("content", String.class)))
                .all()
                .flatMap(this::decompressAndUpdate, 4)
                .count()
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Decompressed {} editorial placement content blobs in {} ms",
                                count, System.currentTimeMillis() - started);
                    }
                })
                .doOnError(err -> log.error("Legacy editorial content decompression failed", err))
                .onErrorReturn(0L)
                .block();
        if (processed != null && processed == 0L) {
            log.debug("No gzipped editorial content found, nothing to decompress");
        }
    }

    private Mono<String> decompressAndUpdate(GzippedRow row) {
        String decompressed = tryDecompress(row.content());
        if (decompressed == null) {
            log.warn("Failed to decompress editorial content for placement {} — skipping", row.id());
            return Mono.empty();
        }
        return databaseClient.sql(UPDATE_DECOMPRESSED)
                .bind("content", decompressed)
                .bind("id", row.id())
                .fetch().rowsUpdated()
                .thenReturn(row.id());
    }

    private static String tryDecompress(String gzipBase64) {
        try {
            byte[] gzipped = Base64.getDecoder().decode(gzipBase64);
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(gzipped));
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[1024];
                int n;
                while ((n = gis.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private record GzippedRow(String id, String content) {}
}
