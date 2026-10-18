package com.storeops.staff.repository;

import com.storeops.staff.model.StaffRole;
import com.storeops.staff.model.User;
import com.storeops.staff.model.UserProfile;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryUserRepository implements UserRepository {

    public static final String STORE_MANAGER_ID = "u-manager";
    public static final String DEPARTMENT_LEAD_ID = "u-lead";
    public static final String ASSOCIATE_ID = "u-associate";
    public static final String REGIONAL_MANAGER_ID = "u-regional";
    public static final String STORE_1 = "store-1";

    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, UserProfile> profiles = new ConcurrentHashMap<>();

    public InMemoryUserRepository() {
        seed(new User(STORE_MANAGER_ID, "Maria Manager", "maria@storeops.test", STORE_1, StaffRole.STORE_MANAGER), "Management");
        seed(new User(DEPARTMENT_LEAD_ID, "Dan Lead", "dan@storeops.test", STORE_1, StaffRole.DEPARTMENT_LEAD), "Grocery");
        seed(new User(ASSOCIATE_ID, "Alex Associate", "alex@storeops.test", STORE_1, StaffRole.ASSOCIATE), "Grocery");
        seed(new User(REGIONAL_MANAGER_ID, "Rita Regional", "rita@storeops.test", "region-1", StaffRole.REGIONAL_MANAGER),
            "Region");
    }

    private void seed(User user, String department) {
        users.put(user.id(), user);
        profiles.put(user.id(), new UserProfile(user.id(), department, "555-0100"));
    }

    @Override
    public Optional<User> findById(String id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public List<User> findByStoreId(String storeId) {
        return users.values().stream().filter(u -> u.storeId().equals(storeId)).toList();
    }

    @Override
    public Optional<UserProfile> findProfile(String userId) {
        return Optional.ofNullable(profiles.get(userId));
    }
}
