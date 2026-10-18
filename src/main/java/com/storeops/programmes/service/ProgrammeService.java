package com.storeops.programmes.service;

import com.storeops.programmes.dto.AddMemberRequest;
import com.storeops.programmes.dto.CreateProgrammeRequest;
import com.storeops.programmes.model.Project;
import com.storeops.programmes.model.ProjectMember;
import com.storeops.programmes.repository.ProgrammeRepository;
import com.storeops.shared.error.ConflictError;
import com.storeops.shared.error.NotFoundError;
import com.storeops.shared.error.ValidationError;
import com.storeops.shared.security.AuthenticatedUser;
import com.storeops.staff.model.User;
import com.storeops.staff.service.StaffService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProgrammeService {

    private final ProgrammeRepository programmeRepository;
    private final StaffService staffService;

    public ProgrammeService(ProgrammeRepository programmeRepository, StaffService staffService) {
        this.programmeRepository = programmeRepository;
        this.staffService = staffService;
    }

    public List<Project> listForStore(AuthenticatedUser caller) {
        return programmeRepository.findByStoreId(caller.storeId());
    }

    public Project create(AuthenticatedUser caller, CreateProgrammeRequest request) {
        Project project = new Project(UUID.randomUUID().toString(), request.name(), request.description(),
            caller.storeId(), caller.id(), Instant.now(), List.of());
        return programmeRepository.save(project);
    }

    public Project addMember(AuthenticatedUser caller, String programmeId, AddMemberRequest request) {
        Project project = programmeRepository.findById(programmeId)
            .filter(p -> p.storeId().equals(caller.storeId()))
            .orElseThrow(() -> new NotFoundError("Programme not found: " + programmeId));
        User member = staffService.requireById(request.userId());
        if (!member.storeId().equals(project.storeId())) {
            throw new ValidationError("User " + member.id() + " does not belong to store " + project.storeId());
        }
        if (project.hasMember(member.id())) {
            throw new ConflictError("User " + member.id() + " is already a member of programme " + programmeId);
        }
        return programmeRepository.save(project.withMember(new ProjectMember(member.id(), request.role())));
    }
}
