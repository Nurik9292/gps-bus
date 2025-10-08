package biz.ugur.busroutebackend.client.domain.model;

import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.event.ClientAuthenticatedEvent;
import biz.ugur.busroutebackend.client.domain.event.ClientOtpVerifiedEvent;
import biz.ugur.busroutebackend.client.domain.event.ClientRegisteredEvent;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.Otp;
import biz.ugur.busroutebackend.client.domain.valueobject.Phone;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Builder
@ToString
@EqualsAndHashCode(callSuper = true)
@Table("clients")
@Getter
public class Client extends AggregateRoot<Client, ClientId> {

    @Id
    @Column("id")
    private ClientId id;

    @Column("name")
    private String name;

    @Column("phone")
    private String phoneNumber;

    @Column("otp")
    private String otpCode;

    @Column("otp_verify")
    private Boolean otpVerify;

    @Column("platform")
    private Platform platform;

    @Column("status")
    private ClientStatus status;

    @Column("last_activity")
    private Instant lastActivity;

    @Column("access_token")
    private String accessToken;

    @Column("refresh_token")
    private String refreshToken;



    public static Client create(String name, String phoneNumber, Platform platform) {
        String validatedName = validateNameStatic(name);
        String validatedPhone = validatePhoneStatic(phoneNumber);

        Instant now = Instant.now();

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
                                      Instant lastActivity,
                                      String accessToken,
                                      String refreshToken,
                                      Instant createdAt,
                                      Instant updatedAt,
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
        this.otpCode = "11111";
        this.otpVerify = true;

    }

    public boolean verifyOtpCenter(String inputOtp) {
        if (this.otpCode != null && this.otpCode.equals(inputOtp)) {
            this.otpVerify = true;
            this.status = ClientStatus.ACTIVE;
            this.updatedAt = Instant.now();
            updateActivity();
            return true;
        }
        return false;
    }

    public boolean verifyOtp(String inputOtp) {
        if (this.otpCode != null && this.otpCode.equals(inputOtp)) {
            this.otpVerify = true;
            this.status = ClientStatus.ACTIVE;
            this.updatedAt = Instant.now();
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
            throw new IllegalStateException("Client cannot login with status: " + status);
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
        this.lastActivity = Instant.now();
    }

    public void updateTokens(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        updateActivity();
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

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Client name cannot be null or empty");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Client name cannot exceed 100 characters");
        }
        return name.trim();
    }

    private String validatePhoneNumber(String phoneNumber) {
        Phone phone = Phone.of(phoneNumber);
        return phone.getValue();
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
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public Instant getUpdatedAt() {
        return updatedAt;
    }


    private static String validateNameStatic(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Client name cannot be null or empty");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Client name cannot exceed 100 characters");
        }
        return name.trim();
    }

    private static String validatePhoneStatic(String phoneNumber) {
        Phone phone = Phone.of(phoneNumber);
        return phone.getValue();
    }
}