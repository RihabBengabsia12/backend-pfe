package tn.rihab.projectservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.rihab.projectservice.model.entity.AnonymizationDict;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnonymizationDictRepository extends JpaRepository<AnonymizationDict, Long> {
    
    List<AnonymizationDict> findByIsActiveTrue();
    
    Optional<AnonymizationDict> findByOriginalWordIgnoreCase(String originalWord);
    
    boolean existsByOriginalWordIgnoreCase(String originalWord);
}
