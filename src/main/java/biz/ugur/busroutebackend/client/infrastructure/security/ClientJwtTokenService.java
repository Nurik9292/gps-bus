package biz.ugur.busroutebackend.client.infrastructure.security;

import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.infrastructure.security.BaseJwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;


@Service
@Slf4j
public class ClientJwtTokenService extends BaseJwtTokenService<ClientPrincipal> {

    private final ClientJwtProperties clientJwtProperties;
    private final ClientRepository clientRepository;

    public ClientJwtTokenService(ObjectMapper objectMapper,
                                 ClientJwtProperties clientJwtProperties,
                                 ClientRepository clientRepository) {
        super(objectMapper, clientJwtProperties.getSecret(), clientJwtProperties.getIssuer());
        this.clientJwtProperties = clientJwtProperties;
        this.clientRepository = clientRepository;
    }

    @Override
    public Mono<String> generateAccessToken(String subjectId, Object... additionalParams) {
        return Mono.fromCallable(() -> {
            Instant now = Instant.now();
            Instant expiration = now.plus(clientJwtProperties.getAccessTokenExpiration());
            log.debug("Generating access token for clientId: {}", subjectId);

            return Jwts.builder()
                    .id(UUID.randomUUID().toString())
                    .subject(subjectId)
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(expiration))
                    .claim("type", "access")
                    .issuer(issuer)
                    .signWith(getSigningKey())
                    .compact();
        });
    }

    @Override
    public Mono<String> generateRefreshToken(String subjectId) {
        return Mono.fromCallable(() -> {
            Instant now = Instant.now();
            Instant expiration = now.plus(clientJwtProperties.getRefreshTokenExpiration());

            log.debug("Generating refresh token for clientId: {}", subjectId);

            return Jwts.builder()
                    .id(UUID.randomUUID().toString())
                    .subject(subjectId)
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(expiration))
                    .claim("type", "refresh")
                    .issuer(issuer)
                    .signWith(getSigningKey())
                    .compact();
        });
    }

    @Override
    public Mono<ClientPrincipal> extractPrincipal(String token) {
        return validateAndExtractClaims(token)
                .flatMap(claims -> validateTokenType(claims, "access"))
                .map(Claims::getSubject)
                .flatMap(clientId -> clientRepository.findById(ClientId.of(clientId)))
                .map(client -> new ClientPrincipal(
                        client.getId().getValue(),
                        client.getPhoneNumber(),
                        client.getStatus().name()
                ))
                .onErrorResume(e -> {
                    log.error("Failed to extract client principal: {}", e.getMessage());
                    return Mono.error(new IllegalArgumentException("Invalid token or client not found"));
                });
    }

    public Mono<String> getClientIdFromToken(String token) {
        return extractSubjectId(token)
                .doOnNext(clientId -> {
                    if (clientId == null || clientId.trim().isEmpty()) {
                        throw new IllegalArgumentException("Token does not contain valid clientId in subject");
                    }
                    log.debug("Extracted clientId from token: {}", clientId);
                });
    }

    public Mono<String> getTokenId(String token) {
        return validateAndExtractClaims(token)
                .map(Claims::getId)
                .doOnError(e -> log.error("Error extracting token ID: {}", e.getMessage()))
                .onErrorReturn(null);
    }
}
