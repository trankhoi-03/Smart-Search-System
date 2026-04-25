package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.dto.SearchStatsDTO;
import com.project.smartsearchsystem.repository.SearchHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {
    @Autowired
    private SearchHistoryRepository searchHistoryRepository;

    // 1. Get Global Top Searches
    public List<SearchStatsDTO> getTopSearches() {
        // Fetch the top 10 results
        List<Object[]> rawResults = searchHistoryRepository.findGlobalTopSearches(PageRequest.of(0, 10));

        return rawResults.stream()
                .map(obj -> new SearchStatsDTO((String) obj[0], (Long) obj[1]))
                .collect(Collectors.toList());
    }

    // 2. Get Missed Inventory (The "Buy these books!" list)
    public List<SearchStatsDTO> getMissedInventory() {
        // Fetch the top 10 missed searches
        List<Object[]> rawResults = searchHistoryRepository.findGlobalMissedSearches(PageRequest.of(0, 10));

        return rawResults.stream()
                .map(obj -> new SearchStatsDTO((String) obj[0], (Long) obj[1]))
                .collect(Collectors.toList());
    }
}
