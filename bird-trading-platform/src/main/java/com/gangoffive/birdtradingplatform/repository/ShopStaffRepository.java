package com.gangoffive.birdtradingplatform.repository;

import com.gangoffive.birdtradingplatform.dto.ShopStaffDto;
import com.gangoffive.birdtradingplatform.entity.ShopOwner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gangoffive.birdtradingplatform.entity.ShopStaff;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopStaffRepository extends JpaRepository<ShopStaff, Long>{
    Optional<ShopStaff> findByUserName(String userName);
    Page<ShopStaff> findByShopOwner(ShopOwner shopOwner, Pageable pageable);
}
