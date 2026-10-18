package com.storeops.programmes.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record Project(String id, String name, String description, String storeId, String createdBy, Instant createdAt,
                      List<ProjectMember> members) {

    public Project {
        members = List.copyOf(members);
    }

    public boolean hasMember(String userId) {
        return members.stream().anyMatch(m -> m.userId().equals(userId));
    }

    public Project withMember(ProjectMember member) {
        List<ProjectMember> updated = new ArrayList<>(members);
        updated.add(member);
        return new Project(id, name, description, storeId, createdBy, createdAt, updated);
    }
}
