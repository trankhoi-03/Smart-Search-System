package com.project.smartsearchsystem.repository;

import com.project.smartsearchsystem.entity.SearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Integer> {
    List<SearchHistory> findTop5UserByIdOrderBySearchTimeDesc(Integer userId);

    @Query("SELECT sh.query, COUNT(sh) as searchCount " +
            "FROM SearchHistory sh " +
            "WHERE sh.userId = :userId " +
            "GROUP BY sh.query " +
            "ORDER BY searchCount DESC")
    List<Object[]> findMostSearchedQueriesByUser(@Param("userId") Integer userId);

    // 1. Global Top Searches (What is everyone looking for?)
    @Query("SELECT sh.query, COUNT(sh) as searchCount " +
            "FROM SearchHistory sh " +
            "GROUP BY sh.query " +
            "ORDER BY searchCount DESC")
    List<Object[]> findGlobalTopSearches(Pageable pageable);

    // 2. Missed Inventory (Searched by users, but 0 local results)
    @Query("SELECT sh.query, COUNT(sh) as missCount " +
            "FROM SearchHistory sh " +
            "WHERE sh.foundLocally = false " +
            "GROUP BY sh.query " +
            "ORDER BY missCount DESC")
    List<Object[]> findGlobalMissedSearches(Pageable pageable);
}
