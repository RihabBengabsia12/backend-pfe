package tn.rihab.projectservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.rihab.projectservice.model.entity.AnonymizationToken;

@Repository
public interface AnonymizationTokenRepository extends JpaRepository<AnonymizationToken, String> {
}
