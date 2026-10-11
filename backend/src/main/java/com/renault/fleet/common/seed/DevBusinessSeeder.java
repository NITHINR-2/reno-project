package com.renault.fleet.common.seed;

import com.renault.fleet.audit.service.AuditEvent;
import com.renault.fleet.audit.service.AuditService;
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
import com.renault.fleet.fleetrequest.repository.FleetRequestRepository;
import com.renault.fleet.inventory.entity.InventoryLocation;
import com.renault.fleet.inventory.entity.InventoryStock;
import com.renault.fleet.inventory.entity.LocationType;
import com.renault.fleet.inventory.repository.InventoryLocationRepository;
import com.renault.fleet.inventory.repository.InventoryStockRepository;
import com.renault.fleet.payment.entity.PaymentMethod;
import com.renault.fleet.payment.entity.PaymentReceipt;
import com.renault.fleet.payment.entity.ReceiptStatus;
import com.renault.fleet.payment.entity.ReceiptType;
import com.renault.fleet.payment.repository.PaymentReceiptRepository;
import com.renault.fleet.production.entity.Plant;
import com.renault.fleet.production.repository.PlantRepository;
import com.renault.fleet.quotation.entity.TaxRate;
import com.renault.fleet.quotation.repository.TaxRateRepository;
import com.renault.fleet.security.AuthenticatedUser;
import com.renault.fleet.sla.service.SlaService;
import com.renault.fleet.user.entity.User;
import com.renault.fleet.user.repository.UserRepository;
import com.renault.fleet.workflow.service.WorkflowHistoryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev profile only: regions, plants, dealers, catalogue, inventory, the Duster GST rate and request FLT-2026-0891.
 * Idempotent (rows are matched by code). Runs after {@code DevUserSeeder}; without a usable user the request and
 * its deposit are skipped, but master data is still seeded.
 */
@Component
@Profile("dev")
@Order(2)
public class DevBusinessSeeder implements ApplicationRunner {

    public static final String SEED_REQUEST = "FLT-2026-0891";
    private static final Logger log = LoggerFactory.getLogger(DevBusinessSeeder.class);

    private final RegionRepository regions;
    private final PlantRepository plants;
    private final DealerRepository dealers;
    private final VehicleModelRepository models;
    private final VehicleVariantRepository variants;
    private final InventoryLocationRepository locations;
    private final InventoryStockRepository stock;
    private final TaxRateRepository taxRates;
    private final FleetRequestRepository requests;
    private final PaymentReceiptRepository receipts;
    private final UserRepository users;
    private final WorkflowHistoryService history;
    private final AuditService audit;
    private final Clock clock;
    private final SlaService sla;
    private final AnalyticsDemoSeed analyticsDemo;

    public DevBusinessSeeder(RegionRepository regions, PlantRepository plants, DealerRepository dealers,
            VehicleModelRepository models, VehicleVariantRepository variants, InventoryLocationRepository locations,
            InventoryStockRepository stock, TaxRateRepository taxRates, FleetRequestRepository requests,
            PaymentReceiptRepository receipts, UserRepository users, WorkflowHistoryService history, AuditService audit,
            Clock clock, SlaService sla, AnalyticsDemoSeed analyticsDemo) {
        this.regions = regions;
        this.plants = plants;
        this.dealers = dealers;
        this.models = models;
        this.variants = variants;
        this.locations = locations;
        this.stock = stock;
        this.taxRates = taxRates;
        this.requests = requests;
        this.receipts = receipts;
        this.users = users;
        this.history = history;
        this.audit = audit;
        this.clock = clock;
        this.sla = sla;
        this.analyticsDemo = analyticsDemo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Region chennai = region("CHENNAI", "Chennai Region");
        Region mumbai = region("MUMBAI", "Mumbai Region");
        Region delhi = region("DELHI_NCR", "Delhi NCR Region");
        Plant pune = plant("PUNE", "Pune Plant", "Pune", "Maharashtra");
        plant("CHENNAI", "Chennai Plant", "Chennai", "Tamil Nadu");

        Dealer chennaiNorth = dealer("CHN-NORTH", "Chennai North Dealership", chennai, "Chennai", "Tamil Nadu",
                "33AABCC1234D1Z5", DealerStatus.ACTIVE);
        dealer("MUM-WEST", "Mumbai West Motors", mumbai, "Mumbai", "Maharashtra", "27AABCM5678E1Z2", DealerStatus.ACTIVE);
        dealer("DEL-CAP", "Delhi Capital Autos", delhi, "New Delhi", "Delhi", "07AABCD9012F1Z8",
                DealerStatus.PENDING_ONBOARDING);

        VehicleVariant duster = variant(model("DUSTER", "Renault Duster", "SUV"), "DUSTER-RXZ-P",
                "Duster RXZ Petrol", "1200000.00");
        VehicleVariant kiger = variant(model("KIGER", "Renault Kiger", "COMPACT_SUV"), "KIGER-RXT-P",
                "Kiger RXT Petrol", "850000.00");
        VehicleVariant triber = variant(model("TRIBER", "Renault Triber", "MPV"), "TRIBER-RXZ-P",
                "Triber RXZ Petrol", "800000.00");
        VehicleVariant kwid = variant(model("KWID", "Renault Kwid", "HATCHBACK"), "KWID-RXL-P",
                "Kwid RXL Petrol", "520000.00");

        InventoryLocation chennaiYard = location("CHN-YARD", "Chennai Yard", LocationType.YARD, chennai, null);
        InventoryLocation puneStore = location("PUNE-PLANT-STORE", "Pune Plant Store", LocationType.PLANT, null, pune);
        InventoryLocation mumbaiWh = location("MUM-WH", "Mumbai Warehouse", LocationType.WAREHOUSE, mumbai, null);
        stock(chennaiYard, duster, 30);
        stock(chennaiYard, kwid, 8);
        stock(mumbaiWh, kiger, 12);
        stock(puneStore, triber, 20);

        // Only the Duster's category is seeded; every rate must be confirmed by Finance (see README).
        if (taxRates.findEffective("SUV", LocalDate.of(2026, 1, 1)).isEmpty()) {
            TaxRate gst = new TaxRate("SUV", new BigDecimal("0.1800"), LocalDate.of(2026, 1, 1));
            gst.setNotes("Dev seed: 18% GST for the Duster as given in the brief. MUST BE CONFIRMED BY FINANCE.");
            taxRates.save(gst);
        }

        seedRequest(chennaiNorth, duster, pune);

        // Executive dashboard demo data: more dealers and regions, and a large yard the demo requests allocate from.
        Region bengaluru = region("BENGALURU", "Bengaluru Region");
        Region hyderabad = region("HYDERABAD", "Hyderabad Region");
        List<Dealer> demoDealers = List.of(chennaiNorth,
                dealers.findByDealerCode("MUM-WEST").orElseThrow(),
                dealer("CHN-SOUTH", "Chennai South Motors", chennai, "Chennai", "Tamil Nadu", "33AABCS2345G1Z3", DealerStatus.ACTIVE),
                dealer("MUM-THANE", "Thane Fleet Renault", mumbai, "Thane", "Maharashtra", "27AABCT3456H1Z4", DealerStatus.ACTIVE),
                dealer("DEL-GGN", "Gurugram Fleet Centre", delhi, "Gurugram", "Haryana", "06AABCG6789L1Z8", DealerStatus.ACTIVE),
                dealer("BLR-EAST", "Bengaluru East Autos", bengaluru, "Bengaluru", "Karnataka", "29AABCB4567J1Z6", DealerStatus.ACTIVE),
                dealer("HYD-HITEC", "Hyderabad Hitec Motors", hyderabad, "Hyderabad", "Telangana", "36AABCH5678K1Z7", DealerStatus.ACTIVE));
        InventoryLocation demoYard = location("BLR-DEMO-YARD", "Bengaluru Yard (demo history)", LocationType.YARD, bengaluru, null);
        if (demoYard.isActive()) {
            // Inactive: it only backs the demo history's allocations and never offers stock to real requests,
            // so FLT-2026-0891 still sees exactly the 30 Chennai yard units.
            demoYard.setActive(false);
            demoYard = locations.save(demoYard);
        }
        for (VehicleVariant v : List.of(duster, kiger, triber, kwid)) {
            stock(demoYard, v, 600);
        }
        seedAnalyticsDemo(demoDealers, List.of(duster, kiger, triber, kwid), List.of(pune.getId(),
                plants.findByCode("CHENNAI").orElseThrow().getId()), demoYard);
    }

    /** About 40 backdated requests over the last 12 months, across dealers, regions, models and stages. */
    private void seedAnalyticsDemo(List<Dealer> demoDealers, List<VehicleVariant> demoVariants, List<Long> plantIds,
            InventoryLocation yard) {
        if (analyticsDemo.alreadySeeded()) {
            return;
        }
        Optional<User> mm = user("marketing.manager");
        Optional<User> mh = user("marketing.head");
        Optional<User> fin = user("finance");
        Optional<User> sup = user("supervisor");
        if (mm.isEmpty() || mh.isEmpty() || fin.isEmpty() || sup.isEmpty()) {
            log.warn("Dev seed: analytics demo needs all four business users; skipped");
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
        List<AnalyticsDemoSeed.Variant> variantsWithStock = demoVariants.stream().map(v -> {
            // Only categories with a GST rate in tax_rates can be quoted (just the Duster's, until Finance confirms more).
            Optional<TaxRate> rate = taxRates.findEffective(v.getModel().getVehicleCategory(), today);
            return new AnalyticsDemoSeed.Variant(v.getId(), v.getModel().getName() + " " + v.getName(), v.getUnitPrice(),
                    rate.map(TaxRate::getId).orElse(null), rate.map(TaxRate::getRate).orElse(null),
                    stock.findByLocationIdAndVariantId(yard.getId(), v.getId()).orElseThrow().getId());
        }).toList();
        List<Long> ids = analyticsDemo.seed(new AnalyticsDemoSeed.Catalog(
                demoDealers.stream().map(Dealer::getId).toList(), variantsWithStock, plantIds, yard.getId(),
                new AnalyticsDemoSeed.Actors(mm.get().getId(), mh.get().getId(), fin.get().getId(), sup.get().getId())),
                40, Instant.now(clock), 6003L);
        audit.record(AuditEvent.of("DEV_SEED", "FLEET_REQUEST", "analytics-demo")
                .withReason("dev profile seed: " + ids.size() + " backdated requests for the executive dashboard"));
        log.info("Dev seed: created {} analytics demo requests", ids.size());
    }

    private void seedRequest(Dealer dealer, VehicleVariant variant, Plant plant) {
        if (requests.findByRequestNumber(SEED_REQUEST).isPresent()) {
            return;
        }
        Optional<User> creator = user("marketing.manager").or(() -> user("supervisor"));
        Optional<User> finance = user("finance").or(() -> user("supervisor"));
        if (creator.isEmpty() || finance.isEmpty()) {
            log.warn("Dev seed: no marketing.manager/finance/supervisor user; skipping {}", SEED_REQUEST);
            return;
        }
        FleetRequest request = new FleetRequest(SEED_REQUEST, dealer, variant, 50, creator.get());
        request.setPlant(plant);
        request.setRequiredDeposit(new BigDecimal("5000000.00"));
        request.setProductionEstimateWeeks(6);
        request.setNotes("Seeded demo request: 50 units, 30 in the Chennai yard, so 20 to be produced at Pune.");
        sla.enterStage(request);
        requests.saveAndFlush(request);
        history.record(request, null, request.getStatus(), "CREATED", AuthenticatedUser.from(creator.get()), null, null);

        Instant now = Instant.now(clock);
        PaymentReceipt deposit = new PaymentReceipt("RCPT-DEV-0891", request, ReceiptType.DEPOSIT, PaymentMethod.NEFT,
                "DEV-SEED-DEP-0891", new BigDecimal("1000000.00"),
                LocalDate.ofInstant(now, ZoneId.of("Asia/Kolkata")), finance.get());
        deposit.setStatus(ReceiptStatus.APPROVED);
        deposit.setReviewedBy(finance.get());
        deposit.setReviewedAt(now);
        receipts.save(deposit);

        audit.record(AuditEvent.of("DEV_SEED", "FLEET_REQUEST", String.valueOf(request.getId()))
                .withReason("dev profile seed: " + SEED_REQUEST + " with an approved ₹10,00,000 deposit"));
        log.info("Dev seed: created {} (PENDING_MARKETING_REVIEW)", SEED_REQUEST);
    }

    private Optional<User> user(String username) {
        return users.findWithAuthoritiesByUsername(username).filter(User::isActive);
    }

    private Region region(String code, String name) {
        return regions.findByCode(code).orElseGet(() -> regions.save(new Region(code, name)));
    }

    private Plant plant(String code, String name, String city, String state) {
        return plants.findByCode(code).orElseGet(() -> plants.save(new Plant(code, name, city, state)));
    }

    private Dealer dealer(String code, String name, Region region, String city, String state, String gstin,
            DealerStatus status) {
        return dealers.findByDealerCode(code).orElseGet(() -> {
            Dealer dealer = new Dealer(code, name, region, city, state);
            dealer.setGstin(gstin);
            dealer.setContactName(name + " Fleet Desk");
            dealer.setContactEmail(code.toLowerCase() + "@dealers.example");
            dealer.setStatus(status);
            if (status != DealerStatus.PENDING_ONBOARDING) {
                dealer.setOnboardedAt(Instant.now(clock));
            }
            return dealers.save(dealer);
        });
    }

    private VehicleModel model(String code, String name, String category) {
        return models.findByCode(code).orElseGet(() -> models.save(new VehicleModel(code, name, category)));
    }

    private VehicleVariant variant(VehicleModel model, String code, String name, String unitPrice) {
        return variants.findByCode(code).orElseGet(() -> variants.save(
                new VehicleVariant(model, code, name, FuelType.PETROL, new BigDecimal(unitPrice))));
    }

    private InventoryLocation location(String code, String name, LocationType type, Region region, Plant plant) {
        return locations.findByCode(code).orElseGet(() -> {
            InventoryLocation location = new InventoryLocation(code, name, type);
            location.setRegion(region);
            location.setPlant(plant);
            return locations.save(location);
        });
    }

    private void stock(InventoryLocation location, VehicleVariant variant, int quantity) {
        if (stock.findByLocationIdAndVariantId(location.getId(), variant.getId()).isEmpty()) {
            stock.save(new InventoryStock(location, variant, quantity));
        }
    }
}
