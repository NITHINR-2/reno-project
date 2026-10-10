package com.renault.fleet.inventory.entity;

import com.renault.fleet.common.persistence.BaseEntity;
import com.renault.fleet.dealer.entity.Region;
import com.renault.fleet.production.entity.Plant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_locations")
public class InventoryLocation extends BaseEntity {

    @Column(nullable = false, length = 30, unique = true)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LocationType locationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_id")
    private Plant plant;

    @Column(nullable = false)
    private boolean active = true;

    protected InventoryLocation() {
    }

    public InventoryLocation(String code, String name, LocationType locationType) {
        this.code = code;
        this.name = name;
        this.locationType = locationType;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocationType getLocationType() { return locationType; }
    public Region getRegion() { return region; }
    public void setRegion(Region region) { this.region = region; }
    public Plant getPlant() { return plant; }
    public void setPlant(Plant plant) { this.plant = plant; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
