package com.storeops.staff.repository;

import com.storeops.staff.model.User;
import com.storeops.staff.model.UserProfile;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(String id);

    List<User> findByStoreId(String storeId);

    Optional<UserProfile> findProfile(String userId);
}
