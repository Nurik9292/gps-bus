package biz.ugur.busroutebackend.interfaces.rest.legal;

import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_LEGAL;

@RestController
@RequestMapping(V1_LEGAL)
@Tag(name = "Legal", description = "Privacy policy and terms of service")
public class LegalApiController extends BaseController {

    private static final String PRIVACY_POLICY_FILE = "legal/PRIVACY_AND_TERMS_ALL_RU.md";

    public LegalApiController(MessageSource messageSource) {
        super(messageSource);
    }

    @Override
    protected String getControllerName() {
        return LegalApiController.class.getSimpleName();
    }

    @GetMapping("/privacy")
    @Operation(summary = "Get privacy policy", description = "Returns privacy policy and terms of service text")
    public Mono<ResponseEntity<ApiResponse<LegalDocumentResponse>>> getPrivacyPolicy() {
        return ok(Mono.fromCallable(this::loadPrivacyPolicy)
                .subscribeOn(Schedulers.boundedElastic()));
    }

    private LegalDocumentResponse loadPrivacyPolicy() throws IOException {
        ClassPathResource resource = new ClassPathResource(PRIVACY_POLICY_FILE);
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new LegalDocumentResponse("privacy_policy", "Политика конфиденциальности", content);
    }

    public record LegalDocumentResponse(
            String type,
            String title,
            String content
    ) {}
}
