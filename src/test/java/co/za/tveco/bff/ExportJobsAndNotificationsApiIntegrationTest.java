package co.za.tveco.bff;

import co.za.tveco.bff.repository.AppNotificationRepository;
import co.za.tveco.bff.repository.EmailOutboxRepository;
import co.za.tveco.bff.repository.ExportJobRepository;
import co.za.tveco.bff.repository.InvoiceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import jakarta.servlet.http.Cookie;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExportJobsAndNotificationsApiIntegrationTest {

        private static final String REFRESH_COOKIE_NAME = "TVECO_REFRESH_TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExportJobRepository exportJobRepository;

    @Autowired
    private AppNotificationRepository appNotificationRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

        @Autowired
        private InvoiceRepository invoiceRepository;

    @BeforeEach
    void cleanData() {
                                invoiceRepository.deleteAll();
        exportJobRepository.deleteAll();
        appNotificationRepository.deleteAll();
        emailOutboxRepository.deleteAll();
    }

        private String loginAndGetToken() throws Exception {
                MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("""
                                                                {
                                                                  "email": "admin@tveco.co.za",
                                                                  "password": "tveco2026"
                                                                }
                                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.accessToken").isString())
                                .andExpect(jsonPath("$.data.email").value("admin@tveco.co.za"))
                                .andReturn();

                return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                                .get("data")
                                .get("accessToken")
                                .asText();
        }

                                private Cookie loginAndGetRefreshCookie() throws Exception {
                                                                MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                                                                                                                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                                                                                                                .content("""
                                                                                                                                                                                                                                                                {
                                                                                                                                                                                                                                                                        "email": "admin@tveco.co.za",
                                                                                                                                                                                                                                                                        "password": "tveco2026"
                                                                                                                                                                                                                                                                }
                                                                                                                                                                                                                                                                """))
                                                                                                                                .andExpect(status().isOk())
                                                                                                                                .andExpect(jsonPath("$.success").value(true))
                                                                                                                                .andReturn();

                                                                        Cookie refreshCookie = loginResult.getResponse().getCookie(REFRESH_COOKIE_NAME);
                                                                        assertThat(refreshCookie).isNotNull();
                                                                        return refreshCookie;
                                }

        private String bearerToken() throws Exception {
                return "Bearer " + loginAndGetToken();
        }

                @Test
                void invoiceCanLinkToExportJob() throws Exception {
                        String authHeader = bearerToken();
                        String createJobPayload = """
                                {
                                  "clientId": null,
                                  "clientSnapshot": {
                                    "companyName": "Kabila Muteba Enterprises",
                                    "contactName": "Kabila Muteba",
                                    "email": "kabila@example.zm",
                                    "phone": "+260970000001"
                                  },
                                  "destinationCountry": "Zambia",
                                  "vehicleDescription": "Toyota Land Cruiser 200",
                                  "sourceChannel": "Website",
                                  "projectValue": 52700,
                                  "estimatedDepartureDate": "2026-07-12",
                                  "estimatedArrivalDate": "2026-08-05",
                                  "notes": "Linked invoice verification"
                                }
                                """;

                        MvcResult createdJob = mockMvc.perform(post("/api/export-jobs")
                                        .header(AUTHORIZATION, authHeader)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(createJobPayload))
                                .andExpect(status().isCreated())
                                .andReturn();

                        String exportJobId = objectMapper.readTree(createdJob.getResponse().getContentAsString())
                                .get("data")
                                .get("id")
                                .asText();

                        String createInvoicePayload = """
                                {
                                  "invoiceNumber": "TVECO-2026-901",
                                  "status": "DRAFT",
                                  "issueDate": "2026-07-01",
                                  "dueDate": "2026-07-15",
                                  "clientId": null,
                                  "exportJobId": "%s",
                                  "clientSnapshot": {
                                    "companyName": "Kabila Muteba Enterprises",
                                    "contactName": "Kabila Muteba",
                                    "email": "kabila@example.zm",
                                    "phone": "+260970000001",
                                    "address": "Lusaka, Zambia"
                                  },
                                  "lineItems": [
                                    {
                                      "name": "Vehicle sourcing",
                                      "description": "Sourcing and verification",
                                      "quantity": 1,
                                      "unitPrice": 52700,
                                      "sortOrder": 0
                                    }
                                  ],
                                  "discountType": null,
                                  "discountValue": 0,
                                  "vatEnabled": false,
                                  "vatRate": 0.15,
                                  "notes": "Test invoice",
                                  "paymentDetails": {
                                    "bank": "FNB",
                                    "accountName": "TVECO",
                                    "accountNumber": "1234567890",
                                    "accountType": "Business",
                                    "branchCode": "250655",
                                    "reference": "TVECO-2026-901"
                                  }
                                }
                                """.formatted(exportJobId);

                        mockMvc.perform(post("/api/invoices")
                                        .header(AUTHORIZATION, authHeader)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(createInvoicePayload))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.exportJobId").value(exportJobId))
                                .andExpect(jsonPath("$.data.total").value(52700));
                }

                @Test
                void invoiceCanLinkToPaymentMilestoneAndStillApplyDiscount() throws Exception {
                        String authHeader = bearerToken();
                        String createJobPayload = """
                                {
                                  "clientId": null,
                                  "clientSnapshot": {
                                    "companyName": "Kabila Muteba Enterprises",
                                    "contactName": "Kabila Muteba",
                                    "email": "kabila@example.zm",
                                    "phone": "+260970000001"
                                  },
                                  "destinationCountry": "Zambia",
                                  "vehicleDescription": "Toyota Land Cruiser 200",
                                  "sourceChannel": "Website",
                                  "projectValue": 52700,
                                  "estimatedDepartureDate": "2026-07-12",
                                  "estimatedArrivalDate": "2026-08-05",
                                  "notes": "Discount-safe milestone billing"
                                }
                                """;

                        MvcResult createdJob = mockMvc.perform(post("/api/export-jobs")
                                        .header(AUTHORIZATION, authHeader)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(createJobPayload))
                                .andExpect(status().isCreated())
                                .andReturn();

                        String exportJobId = objectMapper.readTree(createdJob.getResponse().getContentAsString())
                                .get("data")
                                .get("id")
                                .asText();

                        String createInvoicePayload = """
                                {
                                  "invoiceNumber": "TVECO-2026-912",
                                  "status": "DRAFT",
                                  "issueDate": "2026-07-01",
                                  "dueDate": "2026-07-15",
                                  "clientId": null,
                                  "exportJobId": "%s",
                                  "paymentMilestoneKey": "deposit",
                                  "clientSnapshot": {
                                    "companyName": "Kabila Muteba Enterprises",
                                    "contactName": "Kabila Muteba",
                                    "email": "kabila@example.zm",
                                    "phone": "+260970000001",
                                    "address": "Lusaka, Zambia"
                                  },
                                  "lineItems": [
                                    {
                                      "name": "Deposit",
                                      "description": "Milestone invoice with discount",
                                      "quantity": 1,
                                      "unitPrice": 15810,
                                      "sortOrder": 0
                                    }
                                  ],
                                  "discountType": "PERCENT",
                                  "discountValue": 10,
                                  "vatEnabled": false,
                                  "vatRate": 0.15,
                                  "notes": "10 percent discount applied after milestone correspondence",
                                  "paymentDetails": {
                                    "bank": "FNB",
                                    "accountName": "TVECO",
                                    "accountNumber": "1234567890",
                                    "accountType": "Business",
                                    "branchCode": "250655",
                                    "reference": "TVECO-2026-912"
                                  }
                                }
                                """.formatted(exportJobId);

                        mockMvc.perform(post("/api/invoices")
                                        .header(AUTHORIZATION, authHeader)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(createInvoicePayload))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.exportJobId").value(exportJobId))
                                .andExpect(jsonPath("$.data.paymentMilestoneKey").value("deposit"))
                                .andExpect(jsonPath("$.data.subtotal").value(15810))
                                .andExpect(jsonPath("$.data.discountAmount").value(1581))
                                .andExpect(jsonPath("$.data.total").value(14229));
                }

                @Test
                void invoiceLinkedToMilestoneMustMatchMilestoneSubtotal() throws Exception {
                        String authHeader = bearerToken();
                        String createJobPayload = """
                                {
                                  "clientId": null,
                                  "clientSnapshot": {
                                    "companyName": "Kabila Muteba Enterprises",
                                    "contactName": "Kabila Muteba",
                                    "email": "kabila@example.zm",
                                    "phone": "+260970000001"
                                  },
                                  "destinationCountry": "Zambia",
                                  "vehicleDescription": "Toyota Land Cruiser 200",
                                  "sourceChannel": "Website",
                                  "projectValue": 52700,
                                  "estimatedDepartureDate": "2026-07-12",
                                  "estimatedArrivalDate": "2026-08-05",
                                  "notes": "Milestone mismatch guard"
                                }
                                """;

                        MvcResult createdJob = mockMvc.perform(post("/api/export-jobs")
                                        .header(AUTHORIZATION, authHeader)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(createJobPayload))
                                .andExpect(status().isCreated())
                                .andReturn();

                        String exportJobId = objectMapper.readTree(createdJob.getResponse().getContentAsString())
                                .get("data")
                                .get("id")
                                .asText();

                        String createInvoicePayload = """
                                {
                                  "invoiceNumber": "TVECO-2026-913",
                                  "status": "DRAFT",
                                  "issueDate": "2026-07-01",
                                  "dueDate": "2026-07-15",
                                  "clientId": null,
                                  "exportJobId": "%s",
                                  "paymentMilestoneKey": "deposit",
                                  "clientSnapshot": {
                                    "companyName": "Kabila Muteba Enterprises",
                                    "contactName": "Kabila Muteba",
                                    "email": "kabila@example.zm",
                                    "phone": "+260970000001",
                                    "address": "Lusaka, Zambia"
                                  },
                                  "lineItems": [
                                    {
                                      "name": "Deposit",
                                      "description": "Mismatch test",
                                      "quantity": 1,
                                      "unitPrice": 15000,
                                      "sortOrder": 0
                                    }
                                  ],
                                  "discountType": "PERCENT",
                                  "discountValue": 10,
                                  "vatEnabled": false,
                                  "vatRate": 0.15,
                                  "notes": "This should fail",
                                  "paymentDetails": {
                                    "bank": "FNB",
                                    "accountName": "TVECO",
                                    "accountNumber": "1234567890",
                                    "accountType": "Business",
                                    "branchCode": "250655",
                                    "reference": "TVECO-2026-913"
                                  }
                                }
                                """.formatted(exportJobId);

                        mockMvc.perform(post("/api/invoices")
                                        .header(AUTHORIZATION, authHeader)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(createInvoicePayload))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.success").value(false));
                }

    @Test
    void exportJobLifecycle_create_patch_tracking_delete() throws Exception {
        String authHeader = bearerToken();

        mockMvc.perform(get("/api/export-jobs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        String createPayload = """
                {
                  "clientId": null,
                  "clientSnapshot": {
                    "companyName": "Kabila Muteba Enterprises",
                    "contactName": "Kabila Muteba",
                    "email": "kabila@example.zm",
                    "phone": "+260970000001"
                  },
                  "destinationCountry": "Zambia",
                  "vehicleDescription": "Toyota Land Cruiser 200",
                  "sourceChannel": "Website",
                  "projectValue": 52700,
                  "estimatedDepartureDate": "2026-07-12",
                  "estimatedArrivalDate": "2026-08-05",
                  "notes": "Client requested milestone updates"
                }
                """;

        MvcResult created = mockMvc.perform(post("/api/export-jobs")
                        .header(AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ENQUIRY"))
                .andExpect(jsonPath("$.data.jobNumber").exists())
                .andExpect(jsonPath("$.data.publicTrackingToken").exists())
                .andReturn();

        JsonNode createdData = objectMapper.readTree(created.getResponse().getContentAsString()).get("data");
        UUID id = UUID.fromString(createdData.get("id").asText());
        String token = createdData.get("publicTrackingToken").asText();

        mockMvc.perform(get("/api/export-jobs")
                        .header(AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/export-jobs/tracking/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()));

        mockMvc.perform(patch("/api/export-jobs/{id}", id)
                        .header(AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SOURCING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SOURCING"));

        mockMvc.perform(patch("/api/export-jobs/{id}", id)
                        .header(AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DOCUMENTATION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DOCUMENTATION"));

        mockMvc.perform(patch("/api/export-jobs/{id}", id)
                        .header(AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SHIPPING"));

        mockMvc.perform(delete("/api/export-jobs/{id}", id)
                        .header(AUTHORIZATION, authHeader))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/export-jobs/{id}/cancel", id)
                        .header(AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Client withdrew order\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancellationReason").value("Client withdrew order"));

        mockMvc.perform(get("/api/export-jobs")
                        .header(AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void notificationAndOutboxLifecycle_emit_markRead_retry_dispatch() throws Exception {
        String authHeader = bearerToken();
        String emitPayload = """
                {
                  "eventType": "EXPORT_STATUS_CHANGED",
                  "title": "TVECO-EXP-2026-001 moved to SHIPPING",
                  "message": "Kabila Muteba Enterprises export stage is now SHIPPING.",
                  "referenceId": "job-001",
                  "emailTo": "client@example.com",
                  "emailSubject": "TVECO Export Update",
                  "emailBody": "Your export is now in SHIPPING stage",
                  "emailHtmlBody": "<p>Your export is now in SHIPPING stage</p>"
                }
                """;

        MvcResult emitted = mockMvc.perform(post("/api/notifications/emit")
                        .header(AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emitPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        JsonNode notificationData = objectMapper.readTree(emitted.getResponse().getContentAsString()).get("data");
        String notificationId = notificationData.get("id").asText();

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header(AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId)
                        .header(AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header(AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        MvcResult outbox = mockMvc.perform(get("/api/notifications/outbox")
                        .header(AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();

        JsonNode outboxData = objectMapper.readTree(outbox.getResponse().getContentAsString()).get("data");
        String outboxId = outboxData.get(0).get("id").asText();

        mockMvc.perform(get("/api/notifications/outbox/stats")
                        .header(AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending").value(1))
                .andExpect(jsonPath("$.data.failed").value(0))
                .andExpect(jsonPath("$.data.sent").value(0));

        mockMvc.perform(post("/api/notifications/outbox/{id}/retry", outboxId)
                        .header(AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        MvcResult dispatch = mockMvc.perform(post("/api/notifications/outbox/dispatch")
                        .header(AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode dispatchData = objectMapper.readTree(dispatch.getResponse().getContentAsString()).get("data");
        assertThat(dispatchData.get("skipped").asBoolean()).isTrue();

        mockMvc.perform(delete("/api/notifications/outbox/sent")
                        .header(AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.removed").value(0));
    }

    @Test
    void refreshToken_rotationAndLogoutRevokeOldTokens() throws Exception {
        Cookie firstRefreshCookie = loginAndGetRefreshCookie();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(firstRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn();

        String refreshedAccessToken = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .get("data")
                .get("accessToken")
                .asText();

        Cookie rotatedRefreshCookie = refreshResult.getResponse().getCookie(REFRESH_COOKIE_NAME);
        assertThat(rotatedRefreshCookie).isNotNull();

        mockMvc.perform(get("/api/export-jobs")
                        .header(AUTHORIZATION, "Bearer " + refreshedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(firstRefreshCookie))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(rotatedRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(rotatedRefreshCookie))
                .andExpect(status().isUnauthorized());
    }
}