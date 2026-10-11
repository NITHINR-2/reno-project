package com.renault.fleet.common.seed;

import com.renault.fleet.catalog.entity.VehicleColour;
import com.renault.fleet.catalog.entity.VehicleVariant;
import com.renault.fleet.catalog.repository.VehicleColourRepository;
import com.renault.fleet.catalog.repository.VehicleModelRepository;
import com.renault.fleet.catalog.repository.VehicleVariantRepository;
import com.renault.fleet.inventory.entity.InventoryLocation;
import com.renault.fleet.inventory.entity.InventoryStock;
import com.renault.fleet.inventory.entity.LocationType;
import com.renault.fleet.inventory.repository.InventoryLocationRepository;
import com.renault.fleet.inventory.repository.InventoryStockRepository;
import com.renault.fleet.production.entity.Plant;
import com.renault.fleet.production.repository.PlantRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev-only live stock for the Dealer Portal, after the catalogue colours exist ({@link DevDealerPortalSeeder}): the
 * Pune, Chennai and Bangalore plants each get a plant store holding every active variant in every active colour.
 * Counts are deterministic and some are small, so "last units" and production splits are easy to try. Idempotent:
 * existing rows are never changed.
 */
@Component
@Profile("dev")
@Order(4)
public class DevPlantStockSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevPlantStockSeeder.class);
    private static final List<String> MODELS = List.of("DUSTER", "KIGER", "TRIBER", "KWID");

    private record PlantSpec(String plantCode, String plantName, String city, String state, String storeCode, int base) {
    }

    private static final List<PlantSpec> PLANTS = List.of(
            new PlantSpec("PUNE", "Pune Plant", "Pune", "Maharashtra", "PUNE-PLANT-STORE", 12),
            new PlantSpec("CHENNAI", "Chennai Plant", "Chennai", "Tamil Nadu", "CHN-PLANT-STORE", 7),
            new PlantSpec("BANGALORE", "Bangalore Plant", "Bengaluru", "Karnataka", "BLR-PLANT-STORE", 3));

    private final PlantRepository plants;
    private final InventoryLocationRepository locations;
    private final InventoryStockRepository stock;
    private final VehicleModelRepository models;
    private final VehicleVariantRepository variants;
    private final VehicleColourRepository colours;

    public DevPlantStockSeeder(PlantRepository plants, InventoryLocationRepository locations, InventoryStockRepository stock,
            VehicleModelRepository models, VehicleVariantRepository variants, VehicleColourRepository colours) {
        this.plants = plants;
        this.locations = locations;
        this.stock = stock;
        this.models = models;
        this.variants = variants;
        this.colours = colours;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (PlantSpec spec : PLANTS) {
            Plant plant = plants.findByCode(spec.plantCode())
                    .orElseGet(() -> plants.save(new Plant(spec.plantCode(), spec.plantName(), spec.city(), spec.state())));
            InventoryLocation store = locations.findByCode(spec.storeCode()).orElseGet(() -> {
                InventoryLocation location = new InventoryLocation(spec.storeCode(), spec.plantName() + " Store", LocationType.PLANT);
                location.setPlant(plant);
                return locations.save(location);
            });
            for (int m = 0; m < MODELS.size(); m++) {
                var model = models.findByCode(MODELS.get(m));
                if (model.isEmpty()) {
                    continue;
                }
                List<VehicleVariant> modelVariants = variants.findByModelIdInOrderByCode(List.of(model.get().getId()));
                List<VehicleColour> modelColours = colours.findByModelIdInOrderByName(List.of(model.get().getId()));
                for (int v = 0; v < modelVariants.size(); v++) {
                    for (int c = 0; c < modelColours.size(); c++) {
                        VehicleVariant variant = modelVariants.get(v);
                        VehicleColour colour = modelColours.get(c);
                        if (stock.findByLocationIdAndVariantIdAndColourId(store.getId(), variant.getId(), colour.getId()).isEmpty()) {
                            // 0..base+4 units; the spread gives some empty, some last-few and some plentiful rows.
                            int units = Math.max(0, spec.base() + ((m * 5 + v * 3 + c * 7) % 9) - 4);
                            stock.save(new InventoryStock(store, variant, colour, units));
                            created++;
                        }
                    }
                }
            }
        }
        if (created > 0) {
            log.info("Dev seed: created {} plant stock rows", created);
        }
    }
}
