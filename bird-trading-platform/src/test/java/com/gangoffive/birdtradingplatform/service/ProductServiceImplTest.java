package com.gangoffive.birdtradingplatform.service;

import com.gangoffive.birdtradingplatform.entity.OrderDetail;
import com.gangoffive.birdtradingplatform.repository.ProductRepository;
import com.gangoffive.birdtradingplatform.repository.ReviewRepository;
import com.gangoffive.birdtradingplatform.service.impl.ProductServiceImpl;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
public class ProductServiceImplTest {
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductService productService;
    @Autowired
    private ReviewRepository reviewRepository;
    @Test
    @Transactional
    public void TestCalculation() {
        var product =  productRepository.findById(1l).get();
//        log.info("product {}",product.getId() );
        List<OrderDetail> orders = product.getOrderDetails();
        double result= productService.CalculationRating(orders);
//        log.info("calculation rating {}",result) ;
        assertEquals(3.3, result);
    }

    @Test
    @Transactional
    public void TestMapperCalculation(){
        var product =  productRepository.findById(1l).get();
    }

    @Test
    public void TestSaleOffPercent(){
//        List<Double> list = Arrays.asList(10.0,5.0,2.0);
//        double percent = productService.CalculateSaleOff(list, 100);
//        assertEquals(0.16,percent);
    }
}