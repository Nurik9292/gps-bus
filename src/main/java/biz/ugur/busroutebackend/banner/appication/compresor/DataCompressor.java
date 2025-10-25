package biz.ugur.busroutebackend.banner.appication.compresor;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Component
public class DataCompressor {

    public Mono<String> compressAndEncode(String data) {
        if (data == null || data.isEmpty()) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
            try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
                gzip.write(data.getBytes(StandardCharsets.UTF_8));
                gzip.finish();
                byte[] compressed = outputStream.toByteArray();
                return Base64.getEncoder().encodeToString(compressed);
            } catch (IOException exception) {
                throw new RuntimeException("Failed to compress data", exception);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<String> decodeAndDecompress(String data) {
        if (data == null || data.isEmpty()) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
            byte[] compressedData = Base64.getDecoder().decode(data);
            try(BufferedReader br =
                        new BufferedReader(
                                new InputStreamReader(
                                        new GZIPInputStream(
                                                new ByteArrayInputStream(compressedData)), StandardCharsets.UTF_8))
            ) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }

                return sb.toString();
            } catch (IOException exception) {
                throw new RuntimeException("Failed to decompress data", exception);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
