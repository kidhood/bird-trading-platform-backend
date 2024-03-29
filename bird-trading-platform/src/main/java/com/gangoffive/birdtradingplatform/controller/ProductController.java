package com.gangoffive.birdtradingplatform.controller;

import com.gangoffive.birdtradingplatform.api.response.ErrorResponse;
import com.gangoffive.birdtradingplatform.dto.*;
import com.gangoffive.birdtradingplatform.repository.ProductRepository;
import com.gangoffive.birdtradingplatform.service.ProductService;
import com.gangoffive.birdtradingplatform.util.JsonUtil;
import com.gangoffive.birdtradingplatform.util.ResponseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ProductController {
    private final ProductService productService;
    private final ProductRepository productRepository;

    @GetMapping()
    public List<ProductDto> retrieveAllProduct() {
        return productService.retrieveAllProduct();
    }

    @GetMapping("/products/pages/{pageNumber}")
    public ResponseEntity<?> retrieveProductByPageNumber(@PathVariable int pageNumber) {
        return productService.retrieveProductByPageNumber(pageNumber);
    }

    @GetMapping("/products/by-shop-id")
    public ResponseEntity retrieveAllProduct(@RequestParam int pageNumber, @RequestParam Long shopId) {
        return productService.retrieveProductByShopId(shopId, pageNumber);
    }

    @GetMapping("/products/top-product")
    public ResponseEntity<?> retrieveTopProduct() {
        List<ProductCartDto> result = productService.retrieveTopProduct();
        if(result == null){
            ErrorResponse error = new ErrorResponse(HttpStatus.NOT_FOUND.toString(),
                    "Not found product top product: ");
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/products/search")
    public List<ProductDto> findProductByName(@RequestParam String name) {
        return productService.findProductByName(name);
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<?> findProductById(@PathVariable Long id) {
        return productService.retrieveProductById(id);
    }

    @GetMapping("/products/id")
    public ResponseEntity<?> findProductByListId(@RequestParam("id") long[] ids ) {
        return productService.retrieveProductByListId(ids);
    }

    @GetMapping("/products/filter")
    public ResponseEntity<?> filter(ProductFilterDto productFilterDto){
        log.info("dto {}", productFilterDto);
        return productService.filter(productFilterDto);
    }

    @PostMapping("/shop-owner/products")
    public ResponseEntity<?> addNewProduct(
            @RequestParam("image") List<MultipartFile> multipartFiles,
            @RequestParam(name = "video", required = false) MultipartFile multipartVideo,
            @RequestPart(name = "data") ProductShopOwnerDto productShopOwnerDto
    ) {
        log.info("productShopOwnerDto {}", productShopOwnerDto);
        return productService.addNewProduct(multipartFiles, multipartVideo, productShopOwnerDto);
    }

    @PutMapping("/shop-owner/products/status")
    public ResponseEntity<?> updateListProductStatus(@RequestBody ChangeStatusListIdDto changeStatusListIdDto) {
        return productService.updateListProductStatus(changeStatusListIdDto);
    }

    @PutMapping("/shop-owner/products/quantity")
    public ResponseEntity<?> updateListProductQuantity(@RequestBody List<ProductQuantityShopChangeDto> listProductChange) {
        return productService.updateListProductQuantity(listProductChange);
    }

    @GetMapping("/shop-owner/products")
    public ResponseEntity<?> getAllProductOfShop(@RequestParam String data) {
        try {
            return productService.filterAllProduct(JsonUtil.INSTANCE.getObject(data, ProductShopOwnerFilterDto.class), true, false);
        } catch (Exception e) {
            return ResponseUtils.getErrorResponseBadRequest("Data parse not correct.");
        }
    }

    @GetMapping("/shop-owner/products/{productId}")
    public ResponseEntity<?> getProductDetailForShop(@PathVariable long productId) {
        return productService.getProductDetailForShop(productId);
    }

    @PutMapping("/shop-owner/products")
    public ResponseEntity<?> updateProduct(
            @RequestParam(value = "image", required = false) List<MultipartFile> multipartFiles,
            @RequestParam(name = "video", required = false) MultipartFile multipartVideo,
            @RequestPart(name = "data") ProductUpdateDto productUpdate) {
        return productService.updateProduct(multipartFiles, multipartVideo, productUpdate);
    }

    @GetMapping("/admin/products")
    public ResponseEntity<?> getAllProduct(@RequestParam String data) {
        try {
            return productService.filterAllProduct(JsonUtil.INSTANCE.getObject(data, ProductShopOwnerFilterDto.class), false, true);
        } catch (Exception e) {
            return ResponseUtils.getErrorResponseBadRequest("Data parse not correct.");
        }
    }

    @GetMapping("/products/{productId}/relevant")
    public ResponseEntity<?> getProductRelevant(@PathVariable long productId) {
        return productService.getProductRelevantBaseOnId(productId);
    }

    @GetMapping("/products")
    public ResponseEntity<?> getProductByTagAndShopId(@RequestParam("shopid") long shopId, @RequestParam("tagid") long[] tagId) {
        return productService.retrieveProductByShopidAndTagId(shopId, tagId);
    }

}
