package com.fitcheck.identity.service;

import com.fitcheck.common.exception.ResourceNotFoundException;
import com.fitcheck.identity.entity.UserProfile;
import com.fitcheck.identity.repository.UserProfileRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@AllArgsConstructor
public class UserProfileQueryService {

    private final UserProfileRepository userProfileRepository;

    public UserProfile getById(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User profile not found: " + userId));
    }
}