package biz.ugur.busroutebackend.advertising.infrastructure.persistence.entity;

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
@Table("ad_tariffs")
@EqualsAndHashCode(callSuper = false)
public class AdTariffEntity {

    @Id @Column("id")                    private String id;
    @Column("name")                      private String name;
    @Column("description")               private String description;
    @Column("placement_type")            private String placementType;
    @Column("period")                    private String period;
    @Column("price_amount")              private Long priceAmount;
    @Column("currency")                  private String currency;
    @Column("max_impressions")           private Integer maxImpressions;
    @Column("max_clicks")                private Integer maxClicks;
    @Column("daily_impression_cap")      private Integer dailyImpressionCap;
    @Column("is_active")                 private Boolean isActive;
    @Column("display_order")             private Integer displayOrder;
    @CreatedDate @Column("created_at")   private LocalDateTime createdAt;
    @LastModifiedDate @Column("updated_at") private LocalDateTime updatedAt;
    @Column("version")                   private Long version;
}
