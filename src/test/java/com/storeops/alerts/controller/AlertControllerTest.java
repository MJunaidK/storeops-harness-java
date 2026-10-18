package com.storeops.alerts.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
class AlertControllerTest {

    private static final String MANAGER = "u-manager";
    private static final String LEAD = "u-lead";
    private static final String ASSOCIATE = "u-associate";

    @Autowired
    private MockMvc mvc;

    @Test
    void creatingAssignedTaskNotifiesOnlyTheAssigneeThroughTheEventBus() throws Exception {
        String title = "Event-driven " + UUID.randomUUID();

        mvc.perform(post("/api/activities").header("X-User-Id", MANAGER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"programmeId\":\"p-1\",\"title\":\"" + title + "\",\"assigneeId\":\"" + ASSOCIATE + "\"}"))
            .andExpect(status().isCreated());

        mvc.perform(get("/api/alerts").header("X-User-Id", ASSOCIATE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].title", hasItem("New task: " + title)))
            .andExpect(jsonPath("$[?(@.title == 'New task: " + title + "')].status").value(hasItem("UNREAD")))
            .andExpect(jsonPath("$[?(@.title == 'New task: " + title + "')].channel").value(hasItem("IN_APP")))
            .andExpect(jsonPath("$[*].userId", everyItem(org.hamcrest.Matchers.is(ASSOCIATE))));
        mvc.perform(get("/api/alerts").header("X-User-Id", LEAD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].title", not(hasItem("New task: " + title))));
    }

    @Test
    void blockingATaskEscalatesToOwnerAndAssignee() throws Exception {
        String title = "Blockable " + UUID.randomUUID();
        String body = mvc.perform(post("/api/activities").header("X-User-Id", MANAGER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"programmeId\":\"p-1\",\"title\":\"" + title + "\",\"assigneeId\":\"" + LEAD + "\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mvc.perform(patch("/api/activities/" + id).header("X-User-Id", LEAD).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"BLOCKED\"}")).andExpect(status().isOk());

        for (String user : new String[] {MANAGER, LEAD}) {
            mvc.perform(get("/api/alerts").header("X-User-Id", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.taskId == '" + id + "' && @.type == 'ESCALATION')].message")
                    .value(hasItem(containsString("TODO to BLOCKED"))));
        }
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/alerts")).andExpect(status().isUnauthorized());
    }
}
