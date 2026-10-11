package com.renault.fleet.catalog.entity;

import com.renault.fleet.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A paint colour offered for a model. Names are unique per model (case-insensitive); hex is upper-case #RRGGBB. */
@Entity
@Table(name = "vehicle_model_colours")
public class VehicleColour extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private VehicleModel model;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 7)
    private String hexCode;

    @Column(nullable = false)
    private boolean active = true;

    protected VehicleColour() {
    }

    public VehicleColour(VehicleModel model, String name, String hexCode) {
        this.model = model;
        this.name = name;
        this.hexCode = hexCode;
    }

    public VehicleModel getModel() { return model; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHexCode() { return hexCode; }
    public void setHexCode(String hexCode) { this.hexCode = hexCode; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
