package com.gangoffive.birdtradingplatform.repository;

import com.gangoffive.birdtradingplatform.entity.ShopOwner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gangoffive.birdtradingplatform.entity.Order;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>{
    List<Order> findByShopOwner(ShopOwner shopOwner);
}
