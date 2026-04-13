package com.devscribe.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devscribe.dto.feature.FeatureFlagResponse;
import com.devscribe.dto.feature.UpdateFeatureFlagRequest;
import com.devscribe.entity.FeatureFlag;
import com.devscribe.entity.User;
import com.devscribe.repository.FeatureFlagRepository;
import com.devscribe.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<FeatureFlagResponse> listForCurrentUser() {
        User currentUser = getCurrentUserOrNull();
        Long userId = currentUser != null ? currentUser.getId() : null;

        return featureFlagRepository.findAll().stream()
                .map(flag -> new FeatureFlagResponse(
                flag.getFlagKey(),
                flag.getDescription(),
                flag.isEnabled(),
                flag.getRolloutPercentage(),
                isEnabledForUser(flag, userId)
        ))
                .toList();
    }

    @Transactional
    public FeatureFlagResponse updateFlag(UpdateFeatureFlagRequest request) {
        FeatureFlag flag = featureFlagRepository.findByFlagKey(request.key())
                .orElseGet(() -> FeatureFlag.builder().flagKey(request.key()).build());

        flag.setDescription(request.description());
        flag.setEnabled(request.enabled());
        flag.setRolloutPercentage(request.rolloutPercentage());

        OffsetDateTime now = OffsetDateTime.now();
        if (flag.getCreatedAt() == null) {
            flag.setCreatedAt(now);
        }
        flag.setUpdatedAt(now);

        FeatureFlag saved = featureFlagRepository.save(flag);
        User currentUser = getCurrentUserOrNull();
        Long userId = currentUser != null ? currentUser.getId() : null;

        return new FeatureFlagResponse(
                saved.getFlagKey(),
                saved.getDescription(),
                saved.isEnabled(),
                saved.getRolloutPercentage(),
                isEnabledForUser(saved, userId)
        );
    }

    @Transactional(readOnly = true)
    public boolean isEnabledForCurrentUser(String key) {
        User currentUser = getCurrentUserOrNull();
        Long userId = currentUser != null ? currentUser.getId() : null;

        return featureFlagRepository.findByFlagKey(key)
                .map(flag -> isEnabledForUser(flag, userId))
                .orElse(false);
    }

    private boolean isEnabledForUser(FeatureFlag flag, Long userId) {
        if (!flag.isEnabled()) {
            return false;
        }

        int rollout = Math.max(0, Math.min(100, flag.getRolloutPercentage()));
        if (rollout >= 100) {
            return true;
        }

        if (userId == null) {
            return rollout > 0;
        }

        int bucket = Math.floorMod((flag.getFlagKey() + ":" + userId).hashCode(), 100);
        return bucket < rollout;
    }

    private User getCurrentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return null;
        }

        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }
}
