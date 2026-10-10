package com.renault.fleet.support;

import com.renault.fleet.catalog.entity.FuelType;
import com.renault.fleet.catalog.entity.VehicleModel;
import com.renault.fleet.catalog.entity.VehicleVariant;
import com.renault.fleet.catalog.repository.VehicleModelRepository;
import com.renault.fleet.catalog.repository.VehicleVariantRepository;
import com.renault.fleet.dealer.entity.Dealer;
import com.renault.fleet.dealer.entity.DealerStatus;
import com.renault.fleet.dealer.entity.Region;
import com.renault.fleet.dealer.repository.DealerRepository;
import com.renault.fleet.dealer.repository.RegionRepository;
import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.entity.FleetRequestStatus;
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.inventory.entity.InventoryLocation;
import com.renault.fleet.inventory.entity.InventoryStock;
import com.renault.fleet.inventory.entity.LocationType;
import com.renault.fleet.inventory.repository.InventoryLocationRepository;
import com.renault.fleet.inventory.repository.InventoryStockRepository;
import com.renault.fleet.quotation.entity.TaxRate;
import com.renault.fleet.quotation.repository.TaxRateRepository;
import com.renault.fleet.user.entity.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Creates uniquely coded master data and fleet requests (the Testcontainers database is shared by every test in
 * a context). Each save commits on its own, so fixtures are visible to MockMvc calls and other threads.
 */
@Component
public class TestFleetData {

    /** A dealer, a variant and the region they sit in. */
    public record World(Region region, Dealer dealer, VehicleModel model, VehicleVariant variant) {
    }

    private final RegionRepository regions;
    private final DealerRepository dealers;
    private final VehicleModelRepository models;
    private final VehicleVariantRepository variants;
    private final InventoryLocationRepository locations;
    private final InventoryStockRepository stock;
    private final FleetRequestRepository requests;
    private final TaxRateRepository taxRates;

    public TestFleetData(RegionRepository regions, DealerRepository dealers, VehicleModelRepository models,
            VehicleVariantRepository variants, InventoryLocationRepository locations, InventoryStockRepository stock,
            FleetRequestRepository requests, TaxRateRepository taxRates) {
        this.regions = regions;
        this.dealers = dealers;
        this.models = models;
        this.variants = variants;
        this.locations = locations;
        this.stock = stock;
        this.requests = requests;
        this.taxRates = taxRates;
    }

    public World world() {
        return world(DealerStatus.ACTIVE);
    }

    public World world(DealerStatus dealerStatus) {
        return world(dealerStatus, "T_SUV");
    }

    /** A world whose model has its own vehicle category with a GST rate in force since 2020 (for quotation tests). */
    public World worldWithGst(String rate) {
        String category = "TF_" + suffix();
        taxRates.save(new TaxRate(category, new BigDecimal(rate), LocalDate.of(2020, 1, 1)));
        return world(DealerStatus.ACTIVE, category);
    }

    public World world(DealerStatus dealerStatus, String category) {
        String s = suffix();
        Region region = regions.save(new Region("R-" + s, "Region " + s));
        Dealer dealer = new Dealer("D-" + s, "Dealer " + s, region, "Chennai", "Tamil Nadu");
        dealer.setStatus(dealerStatus);
        if (dealerStatus != DealerStatus.PENDING_ONBOARDING) {
            dealer.setOnboardedAt(Instant.now());
        }
        dealer = dealers.save(dealer);
        VehicleModel model = models.save(new VehicleModel("M-" + s, "Model " + s, category));
        VehicleVariant variant = variants.save(new VehicleVariant(model, "V-" + s, "Variant " + s, FuelType.PETROL,
                new BigDecimal("1200000.00")));
        return new World(region, dealer, model, variant);
    }

    public InventoryStock stock(VehicleVariant variant, int available) {
        return stock(variant, available, true);
    }

    public InventoryStock stock(VehicleVariant variant, int available, boolean activeLocation) {
        String s = suffix();
        InventoryLocation location = new InventoryLocation("L-" + s, "Yard " + s, LocationType.YARD);
        location.setActive(activeLocation);
        return stock.save(new InventoryStock(locations.save(location), variant, available));
    }

    /** A request put straight into {@code status} (fixtures only; real code goes through WorkflowService). */
    public FleetRequest request(World world, int quantity, FleetRequestStatus status, User creator, boolean stockMapped) {
        return request(world, quantity, status, creator, stockMapped, new BigDecimal("500000.00"));
    }

    public FleetRequest request(World world, int quantity, FleetRequestStatus status, User creator, boolean stockMapped,
            BigDecimal requiredDeposit) {
        FleetRequest request = new FleetRequest(requestNumber(), world.dealer(), world.variant(), quantity, creator);
        request.setRequiredDeposit(requiredDeposit);
        request.setStatus(status);
        if (status == FleetRequestStatus.RETURNED_TO_MARKETING) {
            request.setReturnReason("fixture");
        }
        if (status == FleetRequestStatus.CANCELLED) {
            request.setCancelReason("fixture");
        }
        if (stockMapped) {
            request.markStockMapped(creator, Instant.now());
        }
        return requests.save(request);
    }

    public FleetRequest request(World world, int quantity, User creator) {
        return request(world, quantity, FleetRequestStatus.PENDING_MARKETING_REVIEW, creator, false);
    }

    private static String requestNumber() {
        return "FLT-2099-" + ThreadLocalRandom.current().nextLong(10_000_000L, 100_000_000L);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
