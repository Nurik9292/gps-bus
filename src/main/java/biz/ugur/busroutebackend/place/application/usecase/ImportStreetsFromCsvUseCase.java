package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.CsvImportResult;
import biz.ugur.busroutebackend.place.domain.model.Street;
import biz.ugur.busroutebackend.place.domain.model.StreetAlias;
import biz.ugur.busroutebackend.place.domain.repository.StreetAliasRepository;
import biz.ugur.busroutebackend.place.domain.repository.StreetRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ImportStreetsFromCsvUseCase extends BaseUseCase<Mono<ImportStreetsFromCsvUseCase.Input>, CsvImportResult> {

    private final StreetRepository streetRepository;
    private final StreetAliasRepository streetAliasRepository;

    public ImportStreetsFromCsvUseCase(StreetRepository streetRepository,
                                       StreetAliasRepository streetAliasRepository,
                                       CorrelationContextService correlationService,
                                       EventBus eventBus) {
        super(correlationService, eventBus);
        this.streetRepository = streetRepository;
        this.streetAliasRepository = streetAliasRepository;
    }

    public record Input(FilePart file, String cityId) {}

    @Override
    protected Mono<CsvImportResult> process(Mono<Input> request) {
        return request.flatMap(this::processInternal);
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
                        rows.add(ImportPlacesFromCsvUseCase.parseCsvLine(line));
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

                    return processStreetRow(fields, rowNum, defaultCityId)
                            .doOnSuccess(v -> imported[0]++)
                            .onErrorResume(e -> {
                                errors.add("Row " + rowNum + ": " + e.getMessage());
                                skipped[0]++;
                                return Mono.empty();
                            });
                })
                .collectList()
                .map(results -> new CsvImportResult(imported[0], skipped[0], errors))
                .doOnSuccess(r -> log.info("CSV street import: imported={}, skipped={}", r.imported(), r.skipped()));
    }

    private Mono<Street> processStreetRow(String[] fields, int rowNum, String defaultCityId) {
        if (fields.length < 1) {
            return Mono.error(new IllegalArgumentException("Not enough columns"));
        }

        String name = fields[0].trim();
        String nameEn = fields.length > 1 ? fields[1].trim() : null;
        String nameTm = fields.length > 2 ? fields[2].trim() : null;
        String aliasesStr = fields.length > 3 ? fields[3].trim() : null;

        if (name.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Name is empty"));
        }

        Street street = Street.create(name, nameEn, nameTm, defaultCityId);

        return streetRepository.save(street)
                .flatMap(saved -> {
                    if (aliasesStr == null || aliasesStr.isEmpty()) {
                        return Mono.just(saved);
                    }
                    String[] aliases = aliasesStr.split("\\|");
                    return Flux.fromArray(aliases)
                            .filter(a -> !a.trim().isEmpty())
                            .concatMap(alias -> {
                                StreetAlias sa = StreetAlias.create(saved.getId().getValue(), alias.trim(), "ru");
                                return streetAliasRepository.save(sa);
                            })
                            .collectList()
                            .thenReturn(saved);
                });
    }
}
