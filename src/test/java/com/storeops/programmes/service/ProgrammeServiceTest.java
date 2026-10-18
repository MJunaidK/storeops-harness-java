package com.storeops.programmes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.storeops.programmes.dto.AddMemberRequest;
import com.storeops.programmes.dto.CreateProgrammeRequest;
import com.storeops.programmes.model.Project;
import com.storeops.programmes.model.ProjectMember;
import com.storeops.programmes.model.ProjectRole;
import com.storeops.programmes.repository.ProgrammeRepository;
import com.storeops.shared.error.ConflictError;
import com.storeops.shared.error.NotFoundError;
import com.storeops.shared.error.ValidationError;
import com.storeops.shared.security.AuthenticatedUser;
import com.storeops.staff.model.StaffRole;
import com.storeops.staff.model.User;
import com.storeops.staff.service.StaffService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProgrammeServiceTest {

    private static final AuthenticatedUser CALLER = new AuthenticatedUser("m1", "s1", StaffRole.STORE_MANAGER);

    @Mock
    private ProgrammeRepository programmeRepository;
    @Mock
    private StaffService staffService;
    @InjectMocks
    private ProgrammeService service;

    private Project project(List<ProjectMember> members) {
        return new Project("p1", "Spring reset", "d", "s1", "m1", Instant.now(), members);
    }

    @Test
    void listReturnsProgrammesOfCallersStore() {
        when(programmeRepository.findByStoreId("s1")).thenReturn(List.of(project(List.of())));

        assertThat(service.listForStore(CALLER)).extracting(Project::id).containsExactly("p1");
    }

    @Test
    void createStampsStoreAndCreator() {
        when(programmeRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project created = service.create(CALLER, new CreateProgrammeRequest("Spring reset", "desc"));

        assertThat(created.storeId()).isEqualTo("s1");
        assertThat(created.createdBy()).isEqualTo("m1");
        assertThat(created.members()).isEmpty();
        assertThat(created.id()).isNotBlank();
    }

    @Test
    void addMemberAppendsMember() {
        when(programmeRepository.findById("p1")).thenReturn(Optional.of(project(List.of())));
        when(staffService.requireById("u1")).thenReturn(new User("u1", "n", "e", "s1", StaffRole.ASSOCIATE));
        when(programmeRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project updated = service.addMember(CALLER, "p1", new AddMemberRequest("u1", ProjectRole.ASSOCIATE));

        assertThat(updated.members()).containsExactly(new ProjectMember("u1", ProjectRole.ASSOCIATE));
    }

    @Test
    void addMemberToProgrammeOfAnotherStoreIsNotFound() {
        Project foreign = new Project("p9", "n", "d", "other-store", "x", Instant.now(), List.of());
        when(programmeRepository.findById("p9")).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.addMember(CALLER, "p9", new AddMemberRequest("u1", ProjectRole.ASSOCIATE)))
            .isInstanceOf(NotFoundError.class);
    }

    @Test
    void addMemberToUnknownProgrammeIsNotFound() {
        when(programmeRepository.findById("zz")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addMember(CALLER, "zz", new AddMemberRequest("u1", ProjectRole.ASSOCIATE)))
            .isInstanceOf(NotFoundError.class);
    }

    @Test
    void addMemberFromAnotherStoreIsRejected() {
        when(programmeRepository.findById("p1")).thenReturn(Optional.of(project(List.of())));
        when(staffService.requireById("u2")).thenReturn(new User("u2", "n", "e", "s2", StaffRole.ASSOCIATE));

        assertThatThrownBy(() -> service.addMember(CALLER, "p1", new AddMemberRequest("u2", ProjectRole.ASSOCIATE)))
            .isInstanceOf(ValidationError.class);
        verify(programmeRepository, never()).save(any());
    }

    @Test
    void addDuplicateMemberIsConflict() {
        when(programmeRepository.findById("p1"))
            .thenReturn(Optional.of(project(List.of(new ProjectMember("u1", ProjectRole.ASSOCIATE)))));
        when(staffService.requireById("u1")).thenReturn(new User("u1", "n", "e", "s1", StaffRole.ASSOCIATE));

        assertThatThrownBy(() -> service.addMember(CALLER, "p1", new AddMemberRequest("u1", ProjectRole.DEPARTMENT_LEAD)))
            .isInstanceOf(ConflictError.class);
    }
}
