package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.CsvImportResult;
import biz.ugur.busroutebackend.place.domain.model.Place;
import biz.ugur.busroutebackend.place.domain.model.PlaceAlias;
import biz.ugur.busroutebackend.place.domain.model.PlaceCategory;
import biz.ugur.busroutebackend.place.domain.repository.PlaceAliasRepository;
import biz.ugur.busroutebackend.place.domain.repository.PlaceRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.place.domain.events.GeoCatalogBulkChangedEvent;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ImportPlacesFromCsvUseCase extends BaseUseCase<Mono<ImportPlacesFromCsvUseCase.Input>, CsvImportResult> {

    private final PlaceRepository placeRepository;
    private final PlaceAliasRepository placeAliasRepository;

    public ImportPlacesFromCsvUseCase(PlaceRepository placeRepository,
                                      PlaceAliasRepository placeAliasRepository,
                                      CorrelationContextService correlationService,
                                      EventBus eventBus) {
        super(correlationService, eventBus);
        this.placeRepository = placeRepository;
        this.placeAliasRepository = placeAliasRepository;
    }

    public record Input(FilePart file, String cityId) {}

    @Override
    protected Mono<CsvImportResult> process(Mono<Input> request) {
        return request.flatMap(this::processInternal)
                .doOnSuccess(result -> {
                    if (result.imported() > 0) {
                        eventBus.publish(new GeoCatalogBulkChangedEvent());
                    }
                });
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }

    private Mono<CsvImportResult> processInternal(Input input) {
        return DataBufferUtils.join(input.file().content())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return parseCsv(bytes, input.cityId());
                });
    }

    private Mono<CsvImportResult> parseCsv(byte[] content, String defaultCityId) {
        return Mono.fromCallable(() -> {
            List<String[]> rows = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new java.io.ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        rows.add(parseCsvLine(line));
                    }
                }
            }
            return rows;
        }).subscribeOn(Schedulers.boundedElastic())
          .flatMap(rows -> processRows(rows, defaultCityId));
    }

    private Mono<CsvImportResult> processRows(List<String[]> rows, String defaultCityId) {
        List<String> errors = new ArrayList<>();
        int[] imported = {0};
        int[] skipped = {0};

        return Flux.fromIterable(rows)
                .index()
                .concatMap(tuple -> {
                    long idx = tuple.getT1();
                    String[] fields = tuple.getT2();
                    int rowNum = (int) idx + 2; 

                    return processPlaceRow(fields, rowNum, defaultCityId)
                            .doOnSuccess(v -> imported[0]++)
                            .onErrorResume(e -> {
                                errors.add("Row " + rowNum + ": " + e.getMessage());
                                skipped[0]++;
                                return Mono.empty();
                            });
                })
                .collectList()
                .map(results -> new CsvImportResult(imported[0], skipped[0], errors))
                .doOnSuccess(r -> log.info("CSV place import: imported={}, skipped={}", r.imported(), r.skipped()));
    }

    private Mono<Place> processPlaceRow(String[] fields, int rowNum, String defaultCityId) {
        if (fields.length < 7) {
            return Mono.error(new IllegalArgumentException("Not enough columns (expected at least 7)"));
        }

        String name = fields[0].trim();
        String nameEn = fields.length > 1 ? fields[1].trim() : null;
        String nameTm = fields.length > 2 ? fields[2].trim() : null;
        String categoryStr = fields.length > 3 ? fields[3].trim() : null;
        String address = fields.length > 4 ? fields[4].trim() : null;
        String latStr = fields.length > 5 ? fields[5].trim() : null;
        String lngStr = fields.length > 6 ? fields[6].trim() : null;
        String aliasesStr = fields.length > 7 ? fields[7].trim() : null;

        if (name.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Name is empty"));
        }

        PlaceCategory category;
        try {
            category = PlaceCategory.valueOf(categoryStr);
        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("Invalid category: " + categoryStr));
        }

        BigDecimal latitude;
        BigDecimal longitude;
        try {
            latitude = new BigDecimal(latStr);
            longitude = new BigDecimal(lngStr);
        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("Invalid coordinates"));
        }

        if (latitude.doubleValue() < 35.0 || latitude.doubleValue() > 43.0
                || longitude.doubleValue() < 52.0 || longitude.doubleValue() > 67.0) {
            return Mono.error(new IllegalArgumentException("Coordinates out of Turkmenistan bounds"));
        }

        Place place = Place.create(name, nameEn, nameTm, null, address, category, defaultCityId, latitude, longitude, null);

        return placeRepository.save(place)
                .flatMap(saved -> {
                    if (aliasesStr == null || aliasesStr.isEmpty()) {
                        return Mono.just(saved);
                    }
                    String[] aliases = aliasesStr.split("\\|");
                    return Flux.fromArray(aliases)
                            .filter(a -> !a.trim().isEmpty())
                            .concatMap(alias -> {
                                PlaceAlias pa = PlaceAlias.create(saved.getId().getValue(), alias.trim(), "ru");
                                return placeAliasRepository.save(pa);
                            })
                            .collectList()
                            .thenReturn(saved);
                });
    }

    static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }
}
