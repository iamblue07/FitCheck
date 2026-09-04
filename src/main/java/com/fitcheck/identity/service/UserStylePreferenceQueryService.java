package com.fitcheck.identity.service;

import com.fitcheck.identity.repository.UserStylePreferenceRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class UserStylePreferenceQueryService {

    private final UserStylePreferenceRepository userStylePreferenceRepository;

    public Set<UUID> findPreferredStyleTagIds(UUID userId) {
        return userStylePreferenceRepository.findAllByUserId(userId).stream()
                .map(pref -> pref.getStyleTag().getId())
                .collect(Collectors.toSet());
    }
}