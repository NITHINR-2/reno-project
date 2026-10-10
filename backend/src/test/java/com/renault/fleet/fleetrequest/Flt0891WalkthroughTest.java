package com.renault.fleet.fleetrequest;

import static com.renault.fleet.support.AuthFlow.login;
import static com.renault.fleet.support.AuthFlow.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.renault.fleet.TestcontainersConfiguration;
import com.renault.fleet.common.seed.DevBusinessSeeder;
import com.renault.fleet.quotation.repository.TaxRateRepository;
import com.renault.fleet.support.AuthFlow.Session;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The Phase 2 acceptance path, using the real dev seeders: FLT-2026-0891 goes from PENDING_MARKETING_REVIEW to
 * PENDING_FINANCE_CLEARANCE through exactly the HTTP calls a user would make in Swagger.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {
        "DEV_ADMIN_PASSWORD=Walkthrough-Admin-1",
        "DEV_SUPERVISOR_PASSWORD=Walkthrough-Sup-1",
        "DEV_MARKETING_MANAGER_PASSWORD=Walkthrough-MM-1",
        "DEV_MARKETING_HEAD_PASSWORD=Walkthrough-MH-1",
        "DEV_FINANCE_PASSWORD=Walkthrough-Fin-1"})
@Import(TestcontainersConfiguration.class)
class Flt0891WalkthroughTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TaxRateRepository taxRates;

    @Test
    void seededRequestIsDrivenFromMarketingReviewToFinanceClearance() throws Exception {
        assertThat(taxRates.findEffective("SUV", LocalDate.of(2026, 10, 1)))
                .hasValueSatisfying(rate -> assertThat(rate.getRate()).isEqualByComparingTo("0.18"));

        Session mm = session(login(mockMvc, "marketing.manager", "Walkthrough-MM-1").andReturn().getResponse());
        String page = call(get("/api/fleet-requests?q=" + DevBusinessSeeder.SEED_REQUEST), mm)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(page, "$.content[0].id");

        call(get("/api/fleet-requests/" + id), mm)
                .andExpect(jsonPath("$.status").value("PENDING_MARKETING_REVIEW"))
                .andExpect(jsonPath("$.dealer.name").value("Chennai North Dealership"))
                .andExpect(jsonPath("$.dealer.regionName").value("Chennai Region"))
                .andExpect(jsonPath("$.variant.name").value("Duster RXZ Petrol"))
                .andExpect(jsonPath("$.variant.unitPrice").value(1200000.00))
                .andExpect(jsonPath("$.plant.name").value("Pune Plant"))
                .andExpect(jsonPath("$.quantity").value(50))
                .andExpect(jsonPath("$.eligibleStockAvailable").value(30))
                .andExpect(jsonPath("$.requiredDeposit").value(5000000.00))
                .andExpect(jsonPath("$.depositReceived").value(1000000.00))
                .andExpect(jsonPath("$.productionEstimateWeeks").value(6));

        String options = call(get("/api/fleet-requests/" + id + "/stock-options"), mm)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].locationName").value("Chennai Yard"))
                .andExpect(jsonPath("$.content[0].quantityAvailable").value(30))
                .andReturn().getResponse().getContentAsString();
        Integer stockId = JsonPath.read(options, "$.content[0].id");

        call(put("/api/fleet-requests/" + id + "/stock-allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"stockId\":" + stockId + ",\"quantity\":30}]}"), mm)
                .andExpect(jsonPath("$.allocatedQty").value(30))
                .andExpect(jsonPath("$.productionRequired").value(20));
        call(post("/api/fleet-requests/" + id + "/submit-for-qualification"), mm)
                .andExpect(jsonPath("$.status").value("MARKETING_QUALIFIED"));
        call(post("/api/fleet-requests/" + id + "/forward-to-marketing-head"), mm)
                .andExpect(jsonPath("$.status").value("PENDING_EXECUTIVE_APPROVAL"));

        Session mh = session(login(mockMvc, "marketing.head", "Walkthrough-MH-1").andReturn().getResponse());
        call(get("/api/fleet-requests/" + id), mh).andExpect(jsonPath("$.allowedActions", contains("APPROVE", "REJECT")));
        call(post("/api/fleet-requests/" + id + "/approve"), mh)
                .andExpect(jsonPath("$.status").value("EXECUTIVE_APPROVED"));
        call(post("/api/fleet-requests/" + id + "/forward-to-finance"), mh)
                .andExpect(jsonPath("$.status").value("PENDING_FINANCE_CLEARANCE"))
                .andExpect(jsonPath("$.depositReceived").value(1000000.00))
                .andExpect(jsonPath("$.productionRequired").value(20));

        call(get("/api/fleet-requests/" + id + "/history"), mh)
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.content[*].action", contains(
                        "CREATED", "STOCK_MAPPED", "QUALIFY", "FORWARD_TO_EXECUTIVE", "APPROVE", "FORWARD_TO_FINANCE")))
                .andExpect(jsonPath("$.content[5].toStatus").value("PENDING_FINANCE_CLEARANCE"));
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            Session session) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, session.bearer())).andExpect(status().isOk());
    }
}
