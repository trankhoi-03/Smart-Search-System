package com.project.smartsearchsystem.repository;

import com.project.smartsearchsystem.entity.SearchHistory;
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
}
