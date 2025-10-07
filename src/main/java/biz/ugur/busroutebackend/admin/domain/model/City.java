package biz.ugur.busroutebackend.admin.domain.model;

import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Getter
@Table("cities")
public class City extends AggregateRoot<City, CityId> {

    @Id
    @Column("id")
    private CityId id;

    @Column("name")
    private String name;

    @Column("name_tm")
    private String nameTm;

    @Column("is_active")
    private Boolean isActive;

    @Column("display_order")
    private Integer displayOrder;


    public static City create(String name, String nameTm, Integer displayOrder) {
        String validatedName = validateNameStatic(name);

        return builder()
                .id(CityId.generate())
                .name(validatedName)
                .nameTm(nameTm != null ? nameTm.trim() : null)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .isActive(true)
                .build();
    }



    public void updateCity(String name, String nameTm, Integer displayOrder) {
        if (name != null && !name.trim().isEmpty()) {
            this.name = name.trim();
        }
        if (nameTm != null) {
            this.nameTm = nameTm.trim();
        }
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }



    @Override
    public CityId getId() {
        return id;
    }

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("City name cannot be null or empty");
        }
        return name.trim();
    }

    private static String validateNameStatic(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("City name cannot be null or empty");
        }
        return name.trim();
    }
}