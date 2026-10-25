package com.storeops.activities.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class ActivityControllerTest {

    private static final String MANAGER = "u-manager";
    private static final String LEAD = "u-lead";
    private static final String ASSOCIATE = "u-associate";

    @Autowired
    private MockMvc mvc;

    private String createTask(String userId, String programmeId, String title, String extraJson) throws Exception {
        String body = mvc.perform(post("/api/activities").header("X-User-Id", userId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"programmeId\":\"" + programmeId + "\",\"title\":\"" + title + "\"" + extraJson + "}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void createReturnsTaskWithDefaultsAndOwner() throws Exception {
        mvc.perform(post("/api/activities").header("X-User-Id", ASSOCIATE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"programmeId\":\"p-1\",\"title\":\"Restock dairy\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Restock dairy"))
            .andExpect(jsonPath("$.status").value("TODO"))
            .andExpect(jsonPath("$.priority").value("MEDIUM"))
            .andExpect(jsonPath("$.category").value("GENERAL"))
            .andExpect(jsonPath("$.ownerId").value(ASSOCIATE))
            .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void createWithBlankTitleReturnsValidationErrorBody() throws Exception {
        mvc.perform(post("/api/activities").header("X-User-Id", ASSOCIATE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"programmeId\":\"p-1\",\"title\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("title")));
    }

    @Test
    void createWithUnknownAssigneeIsNotFound() throws Exception {
        mvc.perform(post("/api/activities").header("X-User-Id", MANAGER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"programmeId\":\"p-1\",\"title\":\"x\",\"assigneeId\":\"ghost\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void malformedJsonIsValidationError() throws Exception {
        mvc.perform(post("/api/activities").header("X-User-Id", MANAGER).contentType(MediaType.APPLICATION_JSON).content("{nope"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listFiltersByProgrammeIdAndStatus() throws Exception {
        String programme = "p-" + UUID.randomUUID();
        String todoId = createTask(MANAGER, programme, "A", "");
        String doneId = createTask(MANAGER, programme, "B", "");
        mvc.perform(patch("/api/activities/" + doneId).header("X-User-Id", MANAGER).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"DONE\"}")).andExpect(status().isOk());

        mvc.perform(get("/api/activities").header("X-User-Id", LEAD).param("programmeId", programme))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/activities").header("X-User-Id", LEAD).param("programmeId", programme).param("status", "DONE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(doneId))
            .andExpect(jsonPath("$[*].id", not(hasItem(todoId))));
    }

    @Test
    void listWithInvalidStatusIsValidationError() throws Exception {
        mvc.perform(get("/api/activities").header("X-User-Id", LEAD).param("status", "NOPE"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listWithSeededUserReturnsOk() throws Exception {
        mvc.perform(get("/api/activities").header("X-User-Id", MANAGER)).andExpect(status().isOk());
    }

    @Test
    void missingOrUnknownUserHeaderIsUnauthorized() throws Exception {
        mvc.perform(get("/api/activities"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/activities").header("X-User-Id", "   "))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/activities").header("X-User-Id", "ghost"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Unknown user"));
    }

    @Test
    void getReturnsTaskOrNotFound() throws Exception {
        String id = createTask(LEAD, "p-1", "Planogram refresh", ",\"category\":\"PLANOGRAM\",\"priority\":\"HIGH\"");

        mvc.perform(get("/api/activities/" + id).header("X-User-Id", ASSOCIATE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.category").value("PLANOGRAM"))
            .andExpect(jsonPath("$.priority").value("HIGH"));
        mvc.perform(get("/api/activities/does-not-exist").header("X-User-Id", ASSOCIATE))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void patchUpdatesFieldsAndKeepsOthers() throws Exception {
        String id = createTask(MANAGER, "p-1", "Audit shelf labels", ",\"category\":\"AUDIT\"");

        mvc.perform(patch("/api/activities/" + id).header("X-User-Id", MANAGER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\",\"priority\":\"CRITICAL\",\"assigneeId\":\"" + ASSOCIATE + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.priority").value("CRITICAL"))
            .andExpect(jsonPath("$.assigneeId").value(ASSOCIATE))
            .andExpect(jsonPath("$.category").value("AUDIT"))
            .andExpect(jsonPath("$.title").value("Audit shelf labels"));
    }

    @Test
    void patchUnknownTaskIsNotFound() throws Exception {
        mvc.perform(patch("/api/activities/missing").header("X-User-Id", MANAGER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteByNonOwnerAssociateIsForbidden() throws Exception {
        String id = createTask(LEAD, "p-1", "Lead's task", "");

        mvc.perform(delete("/api/activities/" + id).header("X-User-Id", ASSOCIATE))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/activities/" + id).header("X-User-Id", LEAD)).andExpect(status().isOk());
    }

    @Test
    void deleteByOwnerRemovesTask() throws Exception {
        String id = createTask(ASSOCIATE, "p-1", "My task", "");

        mvc.perform(delete("/api/activities/" + id).header("X-User-Id", ASSOCIATE)).andExpect(status().isNoContent());
        mvc.perform(get("/api/activities/" + id).header("X-User-Id", ASSOCIATE)).andExpect(status().isNotFound());
    }

    @Test
    void deleteByStoreManagerRemovesAnyonesTask() throws Exception {
        String id = createTask(ASSOCIATE, "p-1", "Someone else's task", "");

        mvc.perform(delete("/api/activities/" + id).header("X-User-Id", MANAGER)).andExpect(status().isNoContent());
        mvc.perform(get("/api/activities/" + id).header("X-User-Id", MANAGER)).andExpect(status().isNotFound());
    }

    @Test
    void deleteUnknownTaskIsNotFound() throws Exception {
        mvc.perform(delete("/api/activities/missing").header("X-User-Id", MANAGER)).andExpect(status().isNotFound());
    }

    private static String item(String taskId, String status) {
        return "{\"taskId\":\"" + taskId + "\",\"status\":\"" + status + "\"}";
    }

    private ResultActions bulkPatch(String userId, String... items) throws Exception {
        return mvc.perform(patch("/api/activities/bulk-status").header("X-User-Id", userId).contentType(MediaType.APPLICATION_JSON)
            .content("{\"updates\":[" + String.join(",", items) + "]}"));
    }

    private void assertTaskStatus(String id, String expected) throws Exception {
        mvc.perform(get("/api/activities/" + id).header("X-User-Id", MANAGER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(expected));
    }

    // AC-1.1
    @Test
    void bulkStatusUpdatesMixedItemsAndReturnsResultsInOrder() throws Exception {
        String t1 = createTask(MANAGER, "p-" + UUID.randomUUID(), "One",
            ",\"assigneeId\":\"" + ASSOCIATE + "\",\"priority\":\"HIGH\"");
        String t2 = createTask(MANAGER, "p-" + UUID.randomUUID(), "Two",
            ",\"assigneeId\":\"" + ASSOCIATE + "\",\"category\":\"AUDIT\"");
        mvc.perform(patch("/api/activities/" + t2).header("X-User-Id", MANAGER).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isOk());

        bulkPatch(ASSOCIATE, item(t1, "DONE"), item(t2, "BLOCKED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedCount").value(2))
            .andExpect(jsonPath("$.failedCount").value(0))
            .andExpect(jsonPath("$.results[0].taskId").value(t1))
            .andExpect(jsonPath("$.results[0].success").value(true))
            .andExpect(jsonPath("$.results[0].status").value("DONE"))
            .andExpect(jsonPath("$.results[0].errorCode").value(nullValue()))
            .andExpect(jsonPath("$.results[0].message").value(nullValue()))
            .andExpect(jsonPath("$.results[1].taskId").value(t2))
            .andExpect(jsonPath("$.results[1].success").value(true))
            .andExpect(jsonPath("$.results[1].status").value("BLOCKED"))
            .andExpect(jsonPath("$.results[1].errorCode").value(nullValue()))
            .andExpect(jsonPath("$.results[1].message").value(nullValue()));
        mvc.perform(get("/api/activities/" + t1).header("X-User-Id", MANAGER))
            .andExpect(jsonPath("$.status").value("DONE"))
            .andExpect(jsonPath("$.assigneeId").value(ASSOCIATE))
            .andExpect(jsonPath("$.priority").value("HIGH"))
            .andExpect(jsonPath("$.category").value("GENERAL"));
        mvc.perform(get("/api/activities/" + t2).header("X-User-Id", MANAGER))
            .andExpect(jsonPath("$.status").value("BLOCKED"))
            .andExpect(jsonPath("$.assigneeId").value(ASSOCIATE))
            .andExpect(jsonPath("$.priority").value("MEDIUM"))
            .andExpect(jsonPath("$.category").value("AUDIT"));
    }

    // AC-1.2
    @Test
    void bulkStatusMissingTaskFailsOnlyItsItem() throws Exception {
        String t1 = createTask(MANAGER, "p-" + UUID.randomUUID(), "One", "");
        String missing = "T-404-" + UUID.randomUUID();

        bulkPatch(MANAGER, item(missing, "DONE"), item(t1, "DONE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedCount").value(1))
            .andExpect(jsonPath("$.failedCount").value(1))
            .andExpect(jsonPath("$.results[0].taskId").value(missing))
            .andExpect(jsonPath("$.results[0].success").value(false))
            .andExpect(jsonPath("$.results[0].status").value(nullValue()))
            .andExpect(jsonPath("$.results[0].errorCode").value("NOT_FOUND"))
            .andExpect(jsonPath("$.results[0].message").value("Task not found: " + missing))
            .andExpect(jsonPath("$.results[1].taskId").value(t1))
            .andExpect(jsonPath("$.results[1].success").value(true))
            .andExpect(jsonPath("$.results[1].status").value("DONE"));
        assertTaskStatus(t1, "DONE");
    }

    // AC-1.3
    @Test
    void bulkStatusByUnrelatedAssociateIsForbiddenForItem() throws Exception {
        String t1 = createTask(LEAD, "p-" + UUID.randomUUID(), "Lead task", ",\"assigneeId\":\"" + LEAD + "\"");

        bulkPatch(ASSOCIATE, item(t1, "DONE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedCount").value(0))
            .andExpect(jsonPath("$.failedCount").value(1))
            .andExpect(jsonPath("$.results[0].taskId").value(t1))
            .andExpect(jsonPath("$.results[0].success").value(false))
            .andExpect(jsonPath("$.results[0].status").value("TODO"))
            .andExpect(jsonPath("$.results[0].errorCode").value("FORBIDDEN"))
            .andExpect(jsonPath("$.results[0].message").isNotEmpty());
        assertTaskStatus(t1, "TODO");
    }

    // AC-1.5
    @Test
    void bulkStatusOfDoneTaskIsConflictForItem() throws Exception {
        String t1 = createTask(MANAGER, "p-" + UUID.randomUUID(), "Done task", "");
        bulkPatch(MANAGER, item(t1, "DONE")).andExpect(jsonPath("$.updatedCount").value(1));

        bulkPatch(MANAGER, item(t1, "BLOCKED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedCount").value(0))
            .andExpect(jsonPath("$.failedCount").value(1))
            .andExpect(jsonPath("$.results[0].taskId").value(t1))
            .andExpect(jsonPath("$.results[0].success").value(false))
            .andExpect(jsonPath("$.results[0].status").value("DONE"))
            .andExpect(jsonPath("$.results[0].errorCode").value("CONFLICT"))
            .andExpect(jsonPath("$.results[0].message").isNotEmpty());
        assertTaskStatus(t1, "DONE");
    }
}
