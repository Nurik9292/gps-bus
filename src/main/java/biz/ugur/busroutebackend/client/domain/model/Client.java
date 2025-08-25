package biz.ugur.busroutebackend.client.domain.model;

import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.Phone;
import biz.ugur.busroutebackend.client.domain.valueobject.Otp;
import biz.ugur.busroutebackend.client.domain.event.ClientRegisteredEvent;
import biz.ugur.busroutebackend.client.domain.event.ClientAuthenticatedEvent;
import biz.ugur.busroutebackend.client.domain.event.ClientOtpVerifiedEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

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

    public Client() {}

    private Client(String name, String phoneNumber, Platform platform) {
        this.id = ClientId.generate();
        this.name = validateName(name);
        this.phoneNumber = validatePhoneNumber(phoneNumber);
        this.platform = platform;
        this.status = ClientStatus.INACTIVE;
        this.otpVerify = false;
        Instant now = Instant.now();
        this.lastActivity = now;
        this.createdAt = now;
        this.updatedAt = now;
        this.version = 0L;

        registerEvent(new ClientRegisteredEvent(
                this.id.getValue(),
                this.phoneNumber,
                platform.name()
        ));
    }

    public static Client create(String name, String phoneNumber, Platform platform) {
        return new Client(name, phoneNumber, platform);
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
                                      Instant updatedAt) {
        Client client = new Client();
        client.id = id;
        client.name = name;
        client.phoneNumber = phoneNumber;
        client.otpCode = otpCode;
        client.otpVerify = otpVerify;
        client.platform = platform;
        client.status = status;
        client.lastActivity = lastActivity;
        client.createdAt = createdAt;
        client.updatedAt = updatedAt;
        client.accessToken = accessToken;
        client.refreshToken = refreshToken;
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
}