package biz.ugur.busroutebackend.client.domain.model;

import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.event.ClientAuthenticatedEvent;
import biz.ugur.busroutebackend.client.domain.event.ClientCreatedViaServiceEvent;
import biz.ugur.busroutebackend.client.domain.event.ClientOtpVerifiedEvent;
import biz.ugur.busroutebackend.client.domain.event.ClientRegisteredEvent;
import biz.ugur.busroutebackend.client.domain.exceptions.ClientAuthenticationException;
import biz.ugur.busroutebackend.client.domain.exceptions.ClientValidationException;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.Otp;
import biz.ugur.busroutebackend.client.domain.valueobject.Phone;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
public class Client extends AggregateRoot<Client, ClientId> {

    private static final String TEST_CENTER_OTP = "11111";
    private static final int MAX_NAME_LENGTH = 100;

    private ClientId id;
    private String name;
    private String phoneNumber;
    private String otpCode;
    private Boolean otpVerify;
    private Platform platform;
    private ClientStatus status;
    private LocalDateTime lastActivity;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    private String createdByServiceId;
    private String externalUserId;

    public static Client create(String name, String phoneNumber, Platform platform) {
        String validatedName = validateName(name);
        String validatedPhone = validatePhone(phoneNumber);

        LocalDateTime now = LocalDateTime.now();

        Client client = Client.builder()
                .id(ClientId.generate())
                .name(validatedName)
                .phoneNumber(validatedPhone)
                .platform(platform)
                .status(ClientStatus.INACTIVE)
                .otpVerify(false)
                .lastActivity(now)
                .build();

        client.createdAt = now;
        client.updatedAt = now;
        client.version = 0L;

        client.registerEvent(new ClientRegisteredEvent(
                client.id.getValue(),
                client.phoneNumber,
                platform.name()
        ));

        return client;
    }

    public static Client createViaExternalService(String name, String serviceId, String externalUserId) {
        return createViaExternalService(name, serviceId, externalUserId, null);
    }

    public static Client createViaExternalService(String name, String serviceId, String externalUserId, String realPhone) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new ClientValidationException("serviceId", "Service ID cannot be null or empty");
        }
        if (externalUserId == null || externalUserId.isBlank()) {
            throw new ClientValidationException("externalUserId", "External user ID cannot be null or empty");
        }

        String validatedName = validateName(name);
        String phone = resolveIntegrationPhone(serviceId, externalUserId, realPhone);

        LocalDateTime now = LocalDateTime.now();

        Client client = Client.builder()
                .id(ClientId.generate())
                .name(validatedName)
                .phoneNumber(phone)
                .platform(Platform.API)
                .status(ClientStatus.ACTIVE)
                .otpVerify(true)
                .createdByServiceId(serviceId)
                .externalUserId(externalUserId)
                .lastActivity(now)
                .build();

        client.createdAt = now;
        client.updatedAt = now;
        client.version = 0L;

        client.registerEvent(new ClientCreatedViaServiceEvent(
                client.id.getValue(),
                serviceId,
                externalUserId
        ));

        return client;
    }

    public Client linkExternalService(String serviceId, String externalUserId) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new ClientValidationException("serviceId", "Service ID cannot be null or empty");
        }
        if (externalUserId == null || externalUserId.isBlank()) {
            throw new ClientValidationException("externalUserId", "External user ID cannot be null or empty");
        }

        LocalDateTime now = LocalDateTime.now();
        Client linked = this.toBuilder()
                .createdByServiceId(serviceId)
                .externalUserId(externalUserId)
                .status(ClientStatus.ACTIVE)
                .otpVerify(true)
                .lastActivity(now)
                .updatedAt(now)
                .build();

        linked.registerEvent(new ClientCreatedViaServiceEvent(
                this.id.getValue(),
                serviceId,
                externalUserId
        ));

        return linked;
    }

    public Client upgradeIntegrationPhone(String realPhone) {
        String phone = Phone.canonicalWithoutPlus(realPhone);
        return this.toBuilder()
                .phoneNumber(phone)
                .updatedAt(LocalDateTime.now())
                .build();
    }


    private static String resolveIntegrationPhone(String serviceId, String externalUserId, String realPhone) {
        if (realPhone != null && !realPhone.isBlank()) {
            return Phone.canonicalWithoutPlus(realPhone);
        }
        return generateIntegrationPhone(serviceId, externalUserId);
    }


    private static String generateIntegrationPhone(String serviceId, String externalUserId) {
        String serviceHash = serviceId.replace("-", "").substring(0, Math.min(6, serviceId.replace("-", "").length())).toUpperCase();
        String userHash = String.valueOf(Math.abs(externalUserId.hashCode()) % 100000000);
        return "+993INT" + serviceHash + "_" + userHash;
    }

    public static Client fromDatabase(ClientId id,
                                      String name,
                                      String phoneNumber,
                                      String otpCode,
                                      Boolean otpVerify,
                                      Platform platform,
                                      ClientStatus status,
                                      LocalDateTime lastActivity,
                                      String accessToken,
                                      String refreshToken,
                                      LocalDateTime createdAt,
                                      LocalDateTime updatedAt,
                                      Long version,
                                      String createdByServiceId,
                                      String externalUserId) {

        Client client = builder()
                .id(id)
                .name(name)
                .phoneNumber(phoneNumber)
                .otpCode(otpCode)
                .otpVerify(otpVerify)
                .platform(platform)
                .status(status)
                .lastActivity(lastActivity)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .createdByServiceId(createdByServiceId)
                .externalUserId(externalUserId)
                .build();

        client.createdAt = createdAt;
        client.updatedAt = updatedAt;
        client.version = version != null ? version : 0L;

        return client;
    }


    public Client generateOtp() {
        Otp otp = Otp.generate();
        return this.toBuilder()
                .otpCode(otp.getCode())
                .otpVerify(false)
                .build();
    }


    public Client generateOtpForCenter() {
        return this.toBuilder()
                .otpCode(TEST_CENTER_OTP)
                .otpVerify(true)
                .build();
    }


    public VerificationResult verifyOtpCenter(String inputOtp) {
        if (this.otpCode != null && this.otpCode.equals(inputOtp)) {
            LocalDateTime now = LocalDateTime.now();
            Client verified = this.toBuilder()
                    .otpVerify(true)
                    .status(ClientStatus.ACTIVE)
                    .lastActivity(now)
                    .updatedAt(now)
                    .build();
            return new VerificationResult(true, verified);
        }
        return new VerificationResult(false, this);
    }

    public VerificationResult verifyOtp(String inputOtp) {
        if (this.otpCode != null && this.otpCode.equals(inputOtp)) {
            LocalDateTime now = LocalDateTime.now();
            Client verified = this.toBuilder()
                    .otpVerify(true)
                    .status(ClientStatus.ACTIVE)
                    .lastActivity(now)
                    .updatedAt(now)
                    .build();

            verified.registerEvent(new ClientOtpVerifiedEvent(
                    this.id.getValue(),
                    this.phoneNumber
            ));
            return new VerificationResult(true, verified);
        }
        return new VerificationResult(false, this);
    }


    public Client authenticate(String accessToken, String refreshToken) {
        if (!status.canLogin()) {
            throw new ClientAuthenticationException(
                ClientAuthenticationException.AuthErrorType.ACCOUNT_DISABLED,
                this.phoneNumber
            );
        }

        Client authenticated = this.toBuilder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .lastActivity(LocalDateTime.now())
                .build();

        authenticated.registerEvent(new ClientAuthenticatedEvent(
                this.id.getValue(),
                this.platform.name()
        ));

        return authenticated;
    }


    public Client updateActivity() {
        return this.toBuilder()
                .lastActivity(LocalDateTime.now())
                .build();
    }


    public Client updateTokens(String accessToken, String refreshToken) {
        return this.toBuilder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .lastActivity(LocalDateTime.now())
                .build();
    }


    public Client logout() {
        return this.toBuilder()
                .accessToken(null)
                .refreshToken(null)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public boolean isRefreshTokenValid(String providedRefreshToken) {
        return this.refreshToken != null
            && this.refreshToken.equals(providedRefreshToken)
            && this.status.canLogin();
    }

    public Client suspend() {
        return this.toBuilder()
                .status(ClientStatus.SUSPENDED)
                .accessToken(null)
                .refreshToken(null)
                .build();
    }


    public Client block() {
        return this.toBuilder()
                .status(ClientStatus.BLOCKED)
                .accessToken(null)
                .refreshToken(null)
                .build();
    }

    public Client activate() {
        return this.toBuilder()
                .status(ClientStatus.ACTIVE)
                .build();
    }


    public record VerificationResult(boolean verified, Client client) {}


    private static String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ClientValidationException("name", "Client name cannot be null or empty");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ClientValidationException("name", "Client name cannot exceed " + MAX_NAME_LENGTH + " characters");
        }
        return name.trim();
    }


    private static String validatePhone(String phoneNumber) {
        try {
            Phone phone = Phone.of(phoneNumber);
            return phone.getValue();
        } catch (IllegalArgumentException e) {
            throw new ClientValidationException("phone", "Invalid phone number: " + e.getMessage());
        }
    }


    @Override
    public ClientId getId() {
        return id;
    }

    public boolean isActive() {
        return ClientStatus.ACTIVE.equals(this.status);
    }

    public boolean isOtpVerified() {
        return Boolean.TRUE.equals(this.otpVerify);
    }

    public boolean hasValidTokens() {
        return this.accessToken != null && this.refreshToken != null;
    }


    public boolean isCreatedByService() {
        return createdByServiceId != null;
    }


    public boolean belongsToService(String serviceId) {
        return serviceId != null && serviceId.equals(this.createdByServiceId);
    }


    public boolean isApiClient() {
        return Platform.API.equals(this.platform);
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public Long getVersion() {
        return version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }
}