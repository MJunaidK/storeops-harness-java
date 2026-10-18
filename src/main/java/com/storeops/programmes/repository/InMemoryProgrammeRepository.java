package com.storeops.programmes.repository;

import com.storeops.programmes.model.Project;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryProgrammeRepository implements ProgrammeRepository {

    private final Map<String, Project> projects = new ConcurrentHashMap<>();

    @Override
    public Project save(Project project) {
        projects.put(project.id(), project);
        return project;
    }

    @Override
    public Optional<Project> findById(String id) {
        return Optional.ofNullable(projects.get(id));
    }

    @Override
    public List<Project> findByStoreId(String storeId) {
        return projects.values().stream()
            .filter(p -> p.storeId().equals(storeId))
            .sorted(Comparator.comparing(Project::createdAt))
            .toList();
    }
}
