package com.renault.fleet.fleetrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renault.fleet.fleetrequest.entity.FleetRequest;
import com.renault.fleet.fleetrequest.service.ProductionCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProductionCalculatorTest {

    @ParameterizedTest(name = "requested {0}, allocated {1} -> produce {2}")
    @CsvSource({
            "50, 30, 20",   // FLT-2026-0891: 50 requested, 30 in the yard
            "50, 0, 50",
            "50, 50, 0",
            "50, 80, 0",    // more stock than needed never gives negative production
            "1, 0, 1",
            "0, 0, 0"})
    void productionRequiredIsRequestedMinusAllocatedNeverNegative(int requested, int allocated, int expected) {
        assertThat(ProductionCalculator.required(requested, allocated)).isEqualTo(expected);
    }

    @Test
    void negativeQuantitiesAreRejected() {
        assertThatThrownBy(() -> ProductionCalculator.required(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductionCalculator.required(10, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entityKeepsProductionInStepWithQuantityAndAllocation() {
        FleetRequest request = new FleetRequest("FLT-2026-0001", null, null, 50, null);
        assertThat(request.getProductionRequired()).isEqualTo(50);
        request.applyAllocation(30);
        assertThat(request.getProductionRequired()).isEqualTo(20);
        request.setQuantity(40);
        assertThat(request.getProductionRequired()).isEqualTo(10);
        request.applyAllocation(0);
        assertThat(request.getProductionRequired()).isEqualTo(40);
    }
}
