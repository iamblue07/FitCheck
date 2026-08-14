package com.fitcheck.identity.service;

import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.common.taxonomy.StyleTagRepository;
import com.fitcheck.identity.dto.StyleTagResponse;
import com.fitcheck.identity.dto.UserProfileResponse;
import com.fitcheck.identity.dto.UserProfileUpdateRequest;
import com.fitcheck.common.taxonomy.StyleTag;
import com.fitcheck.identity.entity.User;
import com.fitcheck.identity.entity.UserProfile;
import com.fitcheck.identity.entity.UserStylePreference;
import com.fitcheck.identity.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class ProfileService {
   private final UserRepository userRepository;
   private final UserProfileRepository userProfileRepository;
   private final StyleTagRepository styleTagRepository;
   private final UserStylePreferenceRepository userStylePreferenceRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        UserProfile profile = loadProfile(userId);
        return toResponse(profile, currentStyleTags(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UserProfileUpdateRequest request) {
        UserProfile profile = loadProfile(userId);

        if (request.birthDate() != null) {
            profile.setBirthDate(request.birthDate());
        }
        if (request.sex() != null) {
            profile.setSex(request.sex());
        }
        if (request.heightCm() != null) {
            profile.setHeightCm(request.heightCm());
        }
        if (request.weightKg() != null) {
            profile.setWeightKg(request.weightKg());
        }
        if (request.footLengthCm() != null) {
            profile.setFootLengthCm(request.footLengthCm());
        }
        if (request.averageBudgetPerOutfit() != null) {
            profile.setAverageBudgetPerOutfit(request.averageBudgetPerOutfit());
        }
        if (request.currency() != null) {
            profile.setCurrency(request.currency());
        }

        userProfileRepository.save(profile);

        return toResponse(profile, currentStyleTags(userId));
    }

    @Transactional
    public List<StyleTagResponse> updateStylePreferences(UUID userId, List<UUID> styleTagIds) {
        Set<UUID> requestedIds = new LinkedHashSet<>(styleTagIds);

        List<StyleTag> foundTags = styleTagRepository.findAllById(requestedIds);
        if (foundTags.size() != requestedIds.size()) {
            Set<UUID> foundIds = foundTags.stream().map(StyleTag::getId).collect(Collectors.toSet());
            Set<UUID> missingIds = new LinkedHashSet<>(requestedIds);
            missingIds.removeAll(foundIds);
            throw new BadRequestException("Unknown style tag ids: " + missingIds);
        }

        userStylePreferenceRepository.deleteAllByUserId(userId);
        userStylePreferenceRepository.flush();

        User userRef = userRepository.getReferenceById(userId);
        List<UserStylePreference> preferences = foundTags.stream()
                .map(tag -> toPreference(userRef, tag))
                .toList();
        userStylePreferenceRepository.saveAll(preferences);

        return foundTags.stream()
                .map(tag -> new StyleTagResponse(tag.getId(), tag.getName()))
                .toList();
    }

    private UserStylePreference toPreference(User userRef, StyleTag tag) {
        return UserStylePreference.builder()
                .user(userRef)
                .styleTag(tag)
                .build();
    }

    private UserProfile loadProfile(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user has no profile row: " + userId));
    }

    private List<StyleTagResponse> currentStyleTags(UUID userId) {
        return userStylePreferenceRepository.findAllByUserId(userId).stream()
                .map(pref -> new StyleTagResponse(pref.getStyleTag().getId(), pref.getStyleTag().getName()))
                .toList();
    }

    private UserProfileResponse toResponse(UserProfile profile, List<StyleTagResponse> styleTags) {
        return new UserProfileResponse(
                profile.getBirthDate(),
                profile.getSex(),
                profile.getHeightCm(),
                profile.getWeightKg(),
                profile.getFootLengthCm(),
                profile.getAverageBudgetPerOutfit(),
                profile.getCurrency(),
                styleTags
        );
    }


}
