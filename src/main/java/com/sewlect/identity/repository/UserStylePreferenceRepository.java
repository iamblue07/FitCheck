package com.sewlect.identity.repository;

import com.sewlect.identity.entity.UserStylePreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserStylePreferenceRepository extends JpaRepository<UserStylePreference, UUID> {

    List<UserStylePreference> findAllByUserId(UUID userId);

    Long deleteAllByUserId(UUID userId);
}
