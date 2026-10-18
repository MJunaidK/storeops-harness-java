package com.storeops.programmes.controller;

import com.storeops.programmes.dto.AddMemberRequest;
import com.storeops.programmes.dto.CreateProgrammeRequest;
import com.storeops.programmes.model.Project;
import com.storeops.programmes.service.ProgrammeService;
import com.storeops.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/programmes")
public class ProgrammeController {

    private final ProgrammeService programmeService;

    public ProgrammeController(ProgrammeService programmeService) {
        this.programmeService = programmeService;
    }

    @GetMapping
    public List<Project> list(AuthenticatedUser caller) {
        return programmeService.listForStore(caller);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Project create(AuthenticatedUser caller, @Valid @RequestBody CreateProgrammeRequest request) {
        return programmeService.create(caller, request);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public Project addMember(AuthenticatedUser caller, @PathVariable String id, @Valid @RequestBody AddMemberRequest request) {
        return programmeService.addMember(caller, id, request);
    }
}
