package com.storeops.activities.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
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
}
