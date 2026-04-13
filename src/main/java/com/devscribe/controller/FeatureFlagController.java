package com.devscribe.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devscribe.dto.feature.FeatureFlagResponse;
import com.devscribe.dto.feature.UpdateFeatureFlagRequest;
import com.devscribe.service.FeatureFlagService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    @GetMapping("/features")
    public ResponseEntity<List<FeatureFlagResponse>> listForCurrentUser() {
        return ResponseEntity.ok(featureFlagService.listForCurrentUser());
    }

    @PutMapping("/admin/features")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeatureFlagResponse> updateFeatureFlag(
            @Valid @RequestBody UpdateFeatureFlagRequest request
    ) {
        return ResponseEntity.ok(featureFlagService.updateFlag(request));
    }
}
