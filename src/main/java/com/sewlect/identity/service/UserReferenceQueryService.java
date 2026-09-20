package com.sewlect.identity.service;

import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@AllArgsConstructor
public class UserReferenceQueryService {

    private final UserRepository userRepository;

    public User getReference(UUID userId) {
        return userRepository.getReferenceById(userId);
    }

    public User getById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}