//package com.gangoffive.birdtradingplatform.repository;
//
//import com.gangoffive.birdtradingplatform.dto.BirdDto;
//import com.gangoffive.birdtradingplatform.dto.ProductDto;
//import com.gangoffive.birdtradingplatform.entity.Accessory;
//import com.gangoffive.birdtradingplatform.entity.Bird;
//import com.gangoffive.birdtradingplatform.entity.Food;
//import com.gangoffive.birdtradingplatform.entity.Product;
//import com.gangoffive.birdtradingplatform.mapper.AccessoryMapper;
//import com.gangoffive.birdtradingplatform.mapper.BirdMapper;
//import com.gangoffive.birdtradingplatform.mapper.FoodMapper;
//import com.gangoffive.birdtradingplatform.mapper.ProductMapper;
//import jakarta.transaction.Transactional;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.data.domain.PageRequest;
//
//import java.awt.print.Pageable;
//import java.util.Collections;
//import java.util.List;
//import java.util.stream.Collectors;
//
//@SpringBootTest
//@Slf4j
//public class ProductRepositoryTest {
//    @Autowired
//    private ProductRepository productRepository;
//    @Autowired
//    private BirdMapper birdMapper;
//    @Autowired
//    private FoodMapper foodMapper;
//    @Autowired
//    private AccessoryMapper accessoryMapper;
//    @Test
//    @Transactional
//    void TestRetrieveData(){
//        PageRequest page = PageRequest.of(0,8);
//        List<ProductDto> lists = productRepository.findAll().stream()
////                .filter(product -> product instanceof Bird)
//                .map(product -> {
//                            if(product instanceof Bird)
//                                return birdMapper.toDto((Bird)product);
//                            else if (product instanceof Food)
//                                return foodMapper.toDto((Food)product);
//                            else if(product instanceof Accessory)
//                                return accessoryMapper.toDto((Accessory) product);
//                    return null;
//                }).
//                collect(Collectors.toList());
//
//        log.info("all product {}", lists);
//    }
//}
