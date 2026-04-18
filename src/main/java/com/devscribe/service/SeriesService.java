package com.devscribe.service;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devscribe.dto.series.CreateSeriesRequest;
import com.devscribe.dto.series.SeriesSummaryResponse;
import com.devscribe.entity.Series;
import com.devscribe.entity.User;
import com.devscribe.repository.SeriesRepository;
import com.devscribe.repository.UserRepository;
import com.devscribe.util.SlugUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SeriesService {

    private final SeriesRepository seriesRepository;
    private final UserRepository userRepository;

    @Transactional
    public SeriesSummaryResponse create(CreateSeriesRequest request) {
        User currentUser = getCurrentUser();
        String normalizedTitle = request.title().trim();
        String slug = createUniqueSlug(normalizedTitle);

        Series series = Series.builder()
                .author(currentUser)
                .slug(slug)
                .title(normalizedTitle)
                .description(normalizeDescription(request.description()))
                .build();

        Series saved = seriesRepository.save(series);
        return toSummary(saved);
    }

    @Transactional(readOnly = true)
    public List<SeriesSummaryResponse> listMine() {
        User currentUser = getCurrentUser();
        return seriesRepository.findByAuthor_IdOrderByUpdatedAtDesc(currentUser.getId()).stream()
                .map(this::toSummary)
                .toList();
    }

    private SeriesSummaryResponse toSummary(Series series) {
        return new SeriesSummaryResponse(
                series.getId(),
                series.getSlug(),
                series.getTitle(),
                series.getDescription(),
                series.getAuthor().getUsername(),
                0,
                series.getCreatedAt(),
                series.getUpdatedAt()
        );
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(FORBIDDEN, "Authentication required");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
    }

    private String createUniqueSlug(String title) {
        String baseSlug = SlugUtil.toSlug(title);
        if (baseSlug.isBlank()) {
            baseSlug = "series";
        }

        String slug = baseSlug;
        int suffix = 1;
        while (seriesRepository.existsBySlug(slug)) {
            suffix += 1;
            slug = baseSlug + "-" + suffix;
        }

        return slug;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }

        String trimmed = description.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed;
    }
}

