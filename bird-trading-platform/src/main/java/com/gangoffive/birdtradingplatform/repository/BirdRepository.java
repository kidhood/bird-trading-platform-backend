
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.gangoffive.birdtradingplatform.repository;

/**
 *
 * @author Admins
 */

import com.gangoffive.birdtradingplatform.dto.ProductFilterDto;
import com.gangoffive.birdtradingplatform.entity.Bird;
import com.gangoffive.birdtradingplatform.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public interface BirdRepository extends JpaRepository<Bird, Long> {
    Optional<List<Bird>> findByNameLike(String name);
    @Query(value = "SELECT product_id FROM `bird-trading-platform`.tbl_bird Where type_id= ?;", nativeQuery =true)
    List<Long> findType(Long typeId);

    @Query(value = "SELECT type_id FROM `bird-trading-platform`.tbl_bird;", nativeQuery = true)
    List<Long> allIdType();

    @Query(value = "SELECT b.product_id " +
            "FROM `bird-trading-platform`.tbl_bird b " +
            "INNER JOIN `bird-trading-platform`.tbl_product_summary ps " +
            "ON b.product_id = ps.product_id " +
            "WHERE b.name LIKE %?1% " +
            "AND b.type_id IN ?2 " +
            "AND ps.star >= ?3 " +
            "AND b.price >= ?4 " +
            "AND b.price <= ?5 " +
            "AND b.is_deleted = 0 " +
            "AND b.quantity > 0 ",
            nativeQuery = true)
    Page<Long> idFilter(String name, List<Long> listType, double star,
                        double lowestPrice, double highestPrice, Pageable pageable);

    Page<Bird> findAllByDeletedFalseAndQuantityGreaterThan(int quantity, Pageable pageable);
    Optional<Page<Product>> findByShopOwner_Id(long id, Pageable pageable);
    @Query(value = "SELECT b.product_id " +
            "FROM `bird-trading-platform`.tbl_bird b " +
            "INNER JOIN `bird-trading-platform`.tbl_product_summary ps " +
            "ON b.product_id = ps.product_id " +
            "WHERE b.shop_id = ?1 " +
            "And b.name LIKE %?2% " +
            "AND b.type_id IN ?3 " +
            "AND ps.star >= ?4 " +
            "AND b.price >= ?5 " +
            "AND b.price <= ?6 " +
            "AND b.is_deleted = 0 " +
            "AND b.quantity > 0 "
            ,
            nativeQuery = true)
    Page<Long> idFilterShop(Long idShop, String name, List<Long> listType, double star,
                        double lowestPrice, double highestPrice, Pageable pageable);


    Optional<Page<Product>> findByShopOwner_IdAndDeletedIsFalse(long id, Pageable pageable);

    Optional<Page<Product>> findByShopOwner_IdAndHiddenIsFalse(long id, Pageable pageable);

    @Query(value = "SELECT * FROM `bird-trading-platform`.tbl_bird b \n" +
            "WHERE b.shop_id = ?1 AND b.product_id = ?2 \n" +
            "AND b.name LIKE %?3% AND b.type_id = ?4 \n" +
            "AND b.price >= ?5", nativeQuery = true)
    Page<Long> filterProductShopOwner(long shopId, long productId, String productName, long typeId, double lowestPrice, Pageable pageable);
}
