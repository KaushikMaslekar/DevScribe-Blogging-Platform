package com.devscribe.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devscribe.entity.FeatureFlag;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, String> {

    Optional<FeatureFlag> findByFlagKey(String flagKey);
}
