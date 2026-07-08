package co.za.tveco.bff;

import co.za.tveco.bff.repository.AppNotificationRepository;
import co.za.tveco.bff.repository.EmailOutboxRepository;
import co.za.tveco.bff.repository.ExportJobRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @BeforeEach
    void cleanData() {
        exportJobRepository.deleteAll();
        appNotificationRepository.deleteAll();
        emailOutboxRepository.deleteAll();
    }

    @Test
    void exportJobLifecycle_create_patch_tracking_delete() throws Exception {
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

        mockMvc.perform(get("/api/export-jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/export-jobs/tracking/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()));

        mockMvc.perform(patch("/api/export-jobs/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SHIPPING"));

        mockMvc.perform(delete("/api/export-jobs/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/export-jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void notificationAndOutboxLifecycle_emit_markRead_retry_dispatch() throws Exception {
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emitPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        JsonNode notificationData = objectMapper.readTree(emitted.getResponse().getContentAsString()).get("data");
        String notificationId = notificationData.get("id").asText();

        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        MvcResult outbox = mockMvc.perform(get("/api/notifications/outbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();

        JsonNode outboxData = objectMapper.readTree(outbox.getResponse().getContentAsString()).get("data");
        String outboxId = outboxData.get(0).get("id").asText();

        mockMvc.perform(get("/api/notifications/outbox/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending").value(1))
                .andExpect(jsonPath("$.data.failed").value(0))
                .andExpect(jsonPath("$.data.sent").value(0));

        mockMvc.perform(post("/api/notifications/outbox/{id}/retry", outboxId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        MvcResult dispatch = mockMvc.perform(post("/api/notifications/outbox/dispatch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode dispatchData = objectMapper.readTree(dispatch.getResponse().getContentAsString()).get("data");
        assertThat(dispatchData.get("skipped").asBoolean()).isTrue();

        mockMvc.perform(delete("/api/notifications/outbox/sent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.removed").value(0));
    }
}