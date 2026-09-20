package com.sewlect.identity.service;

import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.repository.UserProfileRepository;
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