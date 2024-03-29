package com.gangoffive.birdtradingplatform.repository;

import com.gangoffive.birdtradingplatform.entity.VerifyToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VerifyTokenRepository extends JpaRepository<VerifyToken, Long> {
    Optional<VerifyToken> findByTokenAndAccount_Id(int token, Long id);

    Optional<VerifyToken> findByIdAndTokenAndAccount_IdAndRevokedIsTrue(Long verifyId, Integer token, Long id);
}
