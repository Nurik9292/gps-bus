package biz.ugur.busroutebackend.admin.domain.model;

import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

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

    @Transient
    private boolean isNew;

    public City(String name, String nameTm, Integer displayOrder) {
        this.id = CityId.generate();
        this.name = validateName(name);
        this.nameTm = nameTm;
        this.isActive = true;
        this.displayOrder = displayOrder != null ? displayOrder : 0;

        this.isNew = true;
    }

    public City(CityId id, String name, String nameTm, Boolean isActive, Integer displayOrder) {
        this.id = id;
        this.name = name;
        this.nameTm = nameTm;
        this.isActive = isActive;
        this.displayOrder = displayOrder;

        this.isNew = false;
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

    public void markAsExisting() {
        isNew = false;
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
}