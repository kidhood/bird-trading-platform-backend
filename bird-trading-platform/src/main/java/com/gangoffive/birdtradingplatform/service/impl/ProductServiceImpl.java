package com.gangoffive.birdtradingplatform.service.impl;

import com.gangoffive.birdtradingplatform.common.PagingAndSorting;
import com.gangoffive.birdtradingplatform.dto.ProductDto;
import com.gangoffive.birdtradingplatform.entity.*;
import com.gangoffive.birdtradingplatform.api.response.ErrorResponse;
import com.gangoffive.birdtradingplatform.mapper.AccessoryMapper;
import com.gangoffive.birdtradingplatform.mapper.BirdMapper;
import com.gangoffive.birdtradingplatform.mapper.FoodMapper;
import com.gangoffive.birdtradingplatform.repository.ProductRepository;
import com.gangoffive.birdtradingplatform.repository.ProductSummaryRepository;
import com.gangoffive.birdtradingplatform.repository.ReviewRepository;
import com.gangoffive.birdtradingplatform.service.ProductService;
import com.gangoffive.birdtradingplatform.service.ProductSummaryService;
import com.gangoffive.birdtradingplatform.wrapper.PageNumberWraper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final BirdMapper birdMapper;
    private final FoodMapper foodMapper;
    private final AccessoryMapper accessoryMapper;
    private final ProductSummaryRepository productSummaryRepository;
    private final ProductSummaryService productSummaryService;

    @Override
    public List<ProductDto> retrieveAllProduct() {
        List<ProductDto> lists = productRepository.findAll().stream()
                .map(this::apply)
                .collect(Collectors.toList());
        return lists;
    }

    @Override
    public ResponseEntity<?> retrieveProductByPagenumber(int pageNumber) {
        if (pageNumber > 0) {
            pageNumber = pageNumber - 1;
            PageRequest page = PageRequest.of(pageNumber, PagingAndSorting.DEFAULT_PAGE_SIZE);
            Page<Product> pageAble = productRepository.findAll(page);
            List<ProductDto> lists = pageAble.getContent().stream()
                    .map(this::apply)
                    .collect(Collectors.toList());
            pageAble.getTotalPages();
            PageNumberWraper<ProductDto> result = new PageNumberWraper<>(lists, pageAble.getTotalPages());
            return ResponseEntity.ok(result);
        }
        ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.toString(),
                "Page number cannot less than 1");
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @Override
    public double CalculationRating(List<OrderDetail> orderDetails) {
       return productSummaryService.CalculationRating(orderDetails);
    }

    @Override
    public List<ProductDto> retrieveTopProduct() {
        List<Long> birdIds = productSummaryService.getIdTopBird();
        List<Long> accessoryIds = productSummaryService.getIdTopAccessories();
        List<Long> foodIds = productSummaryService.getIdTopFood();

        ArrayList<Long> topProductIds = new ArrayList<>();

        if(birdIds != null && accessoryIds != null && foodIds != null){

            topProductIds.addAll(birdIds.subList(0, 3));
            topProductIds.addAll(accessoryIds.subList(0, 3));
            topProductIds.addAll(foodIds.subList(0, 3));

        }else{
            PageRequest page = PageRequest.of(0, 8, Sort.by(PagingAndSorting.DEFAULT_SORT_DIRECTION, "star")
                                            .and(Sort.by(PagingAndSorting.DEFAULT_SORT_DIRECTION, "totalQuantityOrder")));
            List<ProductSummary> listsTemp =  productSummaryRepository.findAll(page).getContent();
            if(listsTemp != null && listsTemp.size() != 0) {
                topProductIds = (ArrayList<Long>) listsTemp.stream().map(id -> id.getProduct().getId()).toList();
            }
        }
        List<Product> product = productRepository.findAllById(topProductIds);
        List<ProductDto> listDtos = this.listModelToDto(product);
        Collections.shuffle(listDtos);
        return listDtos;
    }

    @Override
    public double CalculateSaleOff(List<PromotionShop> listPromotion, double price) {
        if (listPromotion != null && listPromotion.size() != 0) {
            List<Integer> saleOff = listPromotion.stream().map(s -> s.getRate()).collect(Collectors.toList());
            double priceDiscount = price;
            for (double sale : saleOff) {
                priceDiscount = priceDiscount - priceDiscount * sale / 100;
            }
            double percentDiscount = Math.round(((price - priceDiscount) / price) * 100.0) / 100.0;

            return percentDiscount;
        }
        return 0.0;
    }

    @Override
    public List<ProductDto> listModelToDto(List<Product> products) {
        if (products != null && products.size() != 0) {
            return products.stream()
                    .map(this::apply)
                    .collect(Collectors.toList());
        }
        return null;
    }

    @Override
    public ProductDto retrieveProductById(Long id) {
        Optional<Product> product = productRepository.findById(id);
        if (product.isPresent()) {
            ProductDto productDto = this.apply(product.get());
            return productDto;
        }
        return null;
    }

    @Override
    public List<ProductDto> findProductByName(String name) {
//        PageRequest page = PageRequest.of(pageNumber, 8);
        List<ProductDto> products = productRepository
                .findByNameLike("%" + name + "%")
                .get()
                .stream()
                .map(this::apply)
                .collect(Collectors.toList());
        return products;
    }

    private ProductDto apply(Product product) {
        if (product instanceof Bird) {
            var bird = birdMapper.toDto((Bird) product);
            bird.setStar(this.CalculationRating(product.getOrderDetails()));
            bird.setDiscountRate(this.CalculateSaleOff(product.getPromotionShops(), bird.getPrice()));
            return bird;
        } else if (product instanceof Food) {
            var food = foodMapper.toDto((Food) product);
            food.setStar(this.CalculationRating(product.getOrderDetails()));
            food.setDiscountRate(this.CalculateSaleOff(product.getPromotionShops(), food.getPrice()));
            return food;
        } else if (product instanceof Accessory) {
            var accessory = accessoryMapper.toDto((Accessory) product);
            accessory.setStar(this.CalculationRating(product.getOrderDetails()));
            accessory.setDiscountRate(this.CalculateSaleOff(product.getPromotionShops(), accessory.getPrice()));
            return accessory;
        }
        return null;
    }

}
