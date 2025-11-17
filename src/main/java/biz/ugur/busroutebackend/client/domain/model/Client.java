package biz.ugur.busroutebackend.client.domain.model;

import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.event.ClientAuthenticatedEvent;
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

@Builder
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
                                      Long version) {


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
                .build();

        client.createdAt = createdAt;
        client.updatedAt = updatedAt;
        client.version = version != null ? version : 0L;

        return client;
    }

    public void generateOtp() {
        Otp otp = Otp.generate();
        this.otpCode = otp.getCode();
        this.otpVerify = false;
    }

    public void generateOtpForCenter() {
        this.otpCode = TEST_CENTER_OTP;
        this.otpVerify = true;
    }

    public boolean verifyOtpCenter(String inputOtp) {
        if (this.otpCode != null && this.otpCode.equals(inputOtp)) {
            this.otpVerify = true;
            this.status = ClientStatus.ACTIVE;
            this.updatedAt = LocalDateTime.now();
            updateActivity();
            return true;
        }
        return false;
    }

    public boolean verifyOtp(String inputOtp) {
        if (this.otpCode != null && this.otpCode.equals(inputOtp)) {
            this.otpVerify = true;
            this.status = ClientStatus.ACTIVE;
            this.updatedAt = LocalDateTime.now();
            updateActivity();

            registerEvent(new ClientOtpVerifiedEvent(
                    this.id.getValue(),
                    this.phoneNumber
            ));
            return true;
        }
        return false;
    }

    public void authenticate(String accessToken, String refreshToken) {
        if (!status.canLogin()) {
            throw new ClientAuthenticationException(
                ClientAuthenticationException.AuthErrorType.ACCOUNT_DISABLED,
                this.phoneNumber
            );
        }

        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        updateActivity();

        registerEvent(new ClientAuthenticatedEvent(
                this.id.getValue(),
                this.platform.name()
        ));
    }

    public void updateActivity() {
        this.lastActivity = LocalDateTime.now();
    }

    public void updateTokens(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        updateActivity();
    }

    public void logout() {
        this.accessToken = null;
        this.refreshToken = null;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isRefreshTokenValid(String providedRefreshToken) {
        return this.refreshToken != null
            && this.refreshToken.equals(providedRefreshToken)
            && this.status.canLogin();
    }

    public void suspend() {
        this.status = ClientStatus.SUSPENDED;
        this.accessToken = null;
        this.refreshToken = null;
    }

    public void block() {
        this.status = ClientStatus.BLOCKED;
        this.accessToken = null;
        this.refreshToken = null;
    }

    public void activate() {
        this.status = ClientStatus.ACTIVE;
    }


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