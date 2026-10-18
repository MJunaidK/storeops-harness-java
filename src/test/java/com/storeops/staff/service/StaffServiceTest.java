package com.storeops.staff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storeops.shared.error.NotFoundError;
import com.storeops.staff.model.AuthToken;
import com.storeops.staff.model.StaffRole;
import com.storeops.staff.repository.InMemoryUserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StaffServiceTest {

    private final StaffService service = new StaffService(new InMemoryUserRepository());

    @Test
    void seededUsersCoverAllRequiredRoles() {
        assertThat(service.requireById(InMemoryUserRepository.STORE_MANAGER_ID).role()).isEqualTo(StaffRole.STORE_MANAGER);
        assertThat(service.requireById(InMemoryUserRepository.DEPARTMENT_LEAD_ID).role()).isEqualTo(StaffRole.DEPARTMENT_LEAD);
        assertThat(service.requireById(InMemoryUserRepository.ASSOCIATE_ID).role()).isEqualTo(StaffRole.ASSOCIATE);
    }

    @Test
    void findByIdIsEmptyForUnknownUser() {
        assertThat(service.findById("ghost")).isEmpty();
    }

    @Test
    void requireByIdThrowsNotFound() {
        assertThatThrownBy(() -> service.requireById("ghost")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void listByStoreReturnsOnlyThatStoresStaff() {
        assertThat(service.listByStore(InMemoryUserRepository.STORE_1)).hasSize(3)
            .allMatch(u -> u.storeId().equals(InMemoryUserRepository.STORE_1));
        assertThat(service.listByStore("nowhere")).isEmpty();
    }

    @Test
    void profileLookup() {
        assertThat(service.getProfile(InMemoryUserRepository.ASSOCIATE_ID).department()).isEqualTo("Grocery");
        assertThatThrownBy(() -> service.getProfile("ghost")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void tokenIsIssuedForKnownUserOnly() {
        AuthToken token = service.tokenFor(InMemoryUserRepository.STORE_MANAGER_ID);

        assertThat(token.userId()).isEqualTo(InMemoryUserRepository.STORE_MANAGER_ID);
        assertThat(token.expiresAt()).isAfter(Instant.now());
        assertThatThrownBy(() -> service.tokenFor("ghost")).isInstanceOf(NotFoundError.class);
    }
}
