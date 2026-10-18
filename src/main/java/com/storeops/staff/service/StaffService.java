package com.storeops.staff.service;

import com.storeops.shared.error.NotFoundError;
import com.storeops.staff.model.AuthToken;
import com.storeops.staff.model.User;
import com.storeops.staff.model.UserProfile;
import com.storeops.staff.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Read-only access to staff data; no other module may mutate users. */
@Service
public class StaffService {

    private final UserRepository userRepository;

    public StaffService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    public User requireById(String id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundError("User not found: " + id));
    }

    public List<User> listByStore(String storeId) {
        return userRepository.findByStoreId(storeId);
    }

    public UserProfile getProfile(String userId) {
        return userRepository.findProfile(userId).orElseThrow(() -> new NotFoundError("Profile not found: " + userId));
    }

    public AuthToken tokenFor(String userId) {
        User user = requireById(userId);
        return new AuthToken(UUID.randomUUID().toString(), user.id(), Instant.now().plus(1, ChronoUnit.HOURS));
    }
}
