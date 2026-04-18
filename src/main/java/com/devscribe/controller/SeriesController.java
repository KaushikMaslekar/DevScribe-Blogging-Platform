package com.devscribe.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devscribe.dto.series.CreateSeriesRequest;
import com.devscribe.dto.series.SeriesSummaryResponse;
import com.devscribe.service.SeriesService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/series")
@RequiredArgsConstructor
public class SeriesController {

    private final SeriesService seriesService;

    @PostMapping
    public ResponseEntity<SeriesSummaryResponse> create(@Valid @RequestBody CreateSeriesRequest request) {
        return ResponseEntity.ok(seriesService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<SeriesSummaryResponse>> listMine() {
        return ResponseEntity.ok(seriesService.listMine());
    }
}

