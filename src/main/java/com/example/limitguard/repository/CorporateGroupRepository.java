package com.example.limitguard.repository;

import com.example.limitguard.model.CorporateGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CorporateGroupRepository extends JpaRepository<CorporateGroup, Long> {

    // checks whether a corporate group already has this name
    boolean existsByName(String name);

}
