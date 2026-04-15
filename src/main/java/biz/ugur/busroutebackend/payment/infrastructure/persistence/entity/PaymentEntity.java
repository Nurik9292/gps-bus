package biz.ugur.busroutebackend.payment.infrastructure.persistence.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Builder
@Getter
@Table("payments")
@EqualsAndHashCode(callSuper = false)
public class PaymentEntity {

    @Id @Column("id")                     private String id;
    @Column("provider")                   private String provider;
    @Column("provider_order_id")          private String providerOrderId;
    @Column("order_number")               private String orderNumber;

    @Column("subject_type")               private String subjectType;
    @Column("subject_id")                 private String subjectId;
    @Column("business_id")                private String businessId;

    @Column("amount_minor")               private Long amountMinor;
    @Column("currency")                   private String currency;

    @Column("status")                     private String status;
    @Column("form_url")                   private String formUrl;
    @Column("return_url")                 private String returnUrl;

    @Column("initiated_at")               private LocalDateTime initiatedAt;
    @Column("completed_at")               private LocalDateTime completedAt;
    @Column("failed_at")                  private LocalDateTime failedAt;
    @Column("expires_at")                 private LocalDateTime expiresAt;

    @Column("failure_code")               private String failureCode;
    @Column("failure_message")            private String failureMessage;

    @Column("card_pan_masked")            private String cardPanMasked;
    @Column("card_expiration")            private String cardExpiration;
    @Column("cardholder_name")            private String cardholderName;

    @CreatedDate @Column("created_at")    private LocalDateTime createdAt;
    @LastModifiedDate @Column("updated_at") private LocalDateTime updatedAt;
    @Column("version")                    private Long version;
}
