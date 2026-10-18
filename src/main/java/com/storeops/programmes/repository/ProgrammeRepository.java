package com.storeops.programmes.repository;

import com.storeops.programmes.model.Project;
import java.util.List;
import java.util.Optional;

public interface ProgrammeRepository {

    Project save(Project project);

    Optional<Project> findById(String id);

    List<Project> findByStoreId(String storeId);
}
