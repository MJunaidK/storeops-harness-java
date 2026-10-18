package com.storeops.programmes.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProgrammeControllerTest {

    private static final String MANAGER = "u-manager";
    private static final String ASSOCIATE = "u-associate";
    private static final String REGIONAL = "u-regional";

    @Autowired
    private MockMvc mvc;

    private String createProgramme(String userId, String name) throws Exception {
        String body = mvc.perform(post("/api/programmes").header("X-User-Id", userId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"description\":\"desc\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void createReturnsProgrammeScopedToCallersStore() throws Exception {
        mvc.perform(post("/api/programmes").header("X-User-Id", MANAGER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Spring reset\",\"description\":\"Seasonal reset\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Spring reset"))
            .andExpect(jsonPath("$.storeId").value("store-1"))
            .andExpect(jsonPath("$.createdBy").value(MANAGER))
            .andExpect(jsonPath("$.members").isEmpty());
    }

    @Test
    void createWithBlankNameIsValidationError() throws Exception {
        mvc.perform(post("/api/programmes").header("X-User-Id", MANAGER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name")));
    }

    @Test
    void listShowsOnlyProgrammesOfCallersStore() throws Exception {
        String id = createProgramme(MANAGER, "Visible to store-1");

        mvc.perform(get("/api/programmes").header("X-User-Id", ASSOCIATE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(id)));
        mvc.perform(get("/api/programmes").header("X-User-Id", REGIONAL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", not(hasItem(id))));
    }

    @Test
    void addMemberReturnsProgrammeWithMember() throws Exception {
        String id = createProgramme(MANAGER, "With members");

        mvc.perform(post("/api/programmes/" + id + "/members").header("X-User-Id", MANAGER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + ASSOCIATE + "\",\"role\":\"ASSOCIATE\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.members[0].userId").value(ASSOCIATE))
            .andExpect(jsonPath("$.members[0].role").value("ASSOCIATE"));
    }

    @Test
    void addDuplicateMemberIsConflict() throws Exception {
        String id = createProgramme(MANAGER, "Dupes");
        String body = "{\"userId\":\"" + ASSOCIATE + "\",\"role\":\"ASSOCIATE\"}";
        mvc.perform(post("/api/programmes/" + id + "/members").header("X-User-Id", MANAGER)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());

        mvc.perform(post("/api/programmes/" + id + "/members").header("X-User-Id", MANAGER)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void addMemberValidatesInputAndExistence() throws Exception {
        String id = createProgramme(MANAGER, "Validation");

        mvc.perform(post("/api/programmes/" + id + "/members").header("X-User-Id", MANAGER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/programmes/" + id + "/members").header("X-User-Id", MANAGER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"ghost\",\"role\":\"ASSOCIATE\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/programmes/unknown/members").header("X-User-Id", MANAGER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + ASSOCIATE + "\",\"role\":\"ASSOCIATE\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void addMemberFromAnotherStoreIsValidationError() throws Exception {
        String id = createProgramme(MANAGER, "Cross store");

        mvc.perform(post("/api/programmes/" + id + "/members").header("X-User-Id", MANAGER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + REGIONAL + "\",\"role\":\"ASSOCIATE\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/programmes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/programmes").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
            .andExpect(status().isUnauthorized());
    }
}
