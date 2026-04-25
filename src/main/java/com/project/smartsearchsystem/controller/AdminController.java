package com.project.smartsearchsystem.controller;

import com.project.smartsearchsystem.dto.SearchStatsDTO;
import com.project.smartsearchsystem.service.AdminDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@CrossOrigin(origins = "http://localhost:5173")
public class AdminController {

    @Autowired
    private AdminDashboardService adminDashboardService;

    // 🛡️ SECURITY: Only users with the ADMIN role can hit this endpoint!
    // Note: If your DB stores the role exactly as "ADMIN" (without the "ROLE_" prefix),
    // use hasAuthority('ADMIN'). If it stores "ROLE_ADMIN", use hasRole('ADMIN').
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, List<SearchStatsDTO>>> getDashboardStats() {

        List<SearchStatsDTO> topSearches = adminDashboardService.getTopSearches();
        List<SearchStatsDTO> missedInventory = adminDashboardService.getMissedInventory();

        // Bundle both lists into a single JSON response
        Map<String, List<SearchStatsDTO>> response = new HashMap<>();
        response.put("topSearches", topSearches);
        response.put("missedInventory", missedInventory);

        return ResponseEntity.ok(response);
    }
}
