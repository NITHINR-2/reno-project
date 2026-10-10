package com.renault.fleet.catalog.entity;

import com.renault.fleet.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A vehicle model (e.g. Duster). {@code vehicleCategory} is the key into the effective-dated tax_rates table. */
@Entity
@Table(name = "vehicle_models")
public class VehicleModel extends BaseEntity {

    @Column(nullable = false, length = 30, unique = true)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 40)
    private String vehicleCategory;

    @Column(nullable = false)
    private boolean active = true;

    protected VehicleModel() {
    }

    public VehicleModel(String code, String name, String vehicleCategory) {
        this.code = code;
        this.name = name;
        this.vehicleCategory = vehicleCategory;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVehicleCategory() { return vehicleCategory; }
    public void setVehicleCategory(String vehicleCategory) { this.vehicleCategory = vehicleCategory; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
