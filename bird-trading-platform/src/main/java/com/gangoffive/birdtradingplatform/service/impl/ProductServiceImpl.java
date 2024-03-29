package com.gangoffive.birdtradingplatform.service.impl;

import com.gangoffive.birdtradingplatform.api.response.ErrorResponse;
import com.gangoffive.birdtradingplatform.api.response.SuccessResponse;
import com.gangoffive.birdtradingplatform.common.OrderStatusConstant;
import com.gangoffive.birdtradingplatform.common.PagingAndSorting;
import com.gangoffive.birdtradingplatform.common.ProductStatusConstant;
import com.gangoffive.birdtradingplatform.common.ShopOwnerConstant;
import com.gangoffive.birdtradingplatform.config.AppProperties;
import com.gangoffive.birdtradingplatform.dto.*;
import com.gangoffive.birdtradingplatform.entity.*;
import com.gangoffive.birdtradingplatform.enums.*;
import com.gangoffive.birdtradingplatform.mapper.*;
import com.gangoffive.birdtradingplatform.repository.*;
import com.gangoffive.birdtradingplatform.service.ProductService;
import com.gangoffive.birdtradingplatform.service.ProductSummaryService;
import com.gangoffive.birdtradingplatform.service.PromotionPriceService;
import com.gangoffive.birdtradingplatform.service.ShopOwnerService;
import com.gangoffive.birdtradingplatform.util.FileNameUtils;
import com.gangoffive.birdtradingplatform.util.MyUtils;
import com.gangoffive.birdtradingplatform.util.ResponseUtils;
import com.gangoffive.birdtradingplatform.util.S3Utils;
import com.gangoffive.birdtradingplatform.wrapper.PageNumberWrapper;
import com.gangoffive.birdtradingplatform.wrapper.ProductDetailWrapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final BirdMapper birdMapper;
    private final FoodMapper foodMapper;
    private final AccessoryMapper accessoryMapper;
    private final AddressMapper addressMapper;
    private final ProductSummaryRepository productSummaryRepository;
    private final ProductSummaryService productSummaryService;
    private final PromotionShopMapper promotionShopMapper;
    private final BirdRepository birdRepository;
    private final FoodRepository foodRepository;
    private final AccessoryRepository accessoryRepository;
    private final TypeAccessoryRepository typeAccessoryRepository;
    private final TypeFoodRepository typeFoodRepository;
    private final TypeBirdRepository typeBirdRepository;
    private final AccountRepository accountRepository;
    private final PromotionShopRepository promotionShopRepository;
    private final TagRepository tagRepository;
    private final AppProperties appProperties;
    private final ShopOwnerMapper shopOwnerMapper;
    private final ShopOwnerService shopOwnerService;
    private final PromotionPriceService promotionPriceService;
    private final ShopOwnerRepository shopOwnerRepository;
    private final TypeMapper typeMapper;
    private final TagMapper tagMapper;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;

    @Override
    public List<ProductDto> retrieveAllProduct() {
        List<ProductDto> lists = productRepository.findAll().stream()
                .map(this::ProductToDto)
                .collect(Collectors.toList());
        return lists;
    }

    @Override
    public ResponseEntity<?> retrieveProductByPageNumber(int pageNumber) {
        if (pageNumber > 0) {
            pageNumber = pageNumber - 1;
            PageRequest page = PageRequest.of(pageNumber, PagingAndSorting.DEFAULT_PAGE_SIZE);
            Page<Product> pageAble = productRepository.findAll(page);
            List<ProductDto> lists = pageAble.getContent().stream()
                    .map(this::ProductToDto)
                    .collect(Collectors.toList());
            pageAble.getTotalPages();
            PageNumberWrapper<ProductDto> result = new PageNumberWrapper<>(lists, pageAble.getTotalPages());
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
    public List<ProductCartDto> retrieveTopProduct() {
        List<Long> birdIds = productSummaryService.getIdTopBird();
        List<Long> accessoryIds = productSummaryService.getIdTopAccessories();
        List<Long> foodIds = productSummaryService.getIdTopFood();

        ArrayList<Long> topProductIds = new ArrayList<>();

        if (birdIds != null && accessoryIds != null && foodIds != null) {

            topProductIds.addAll(birdIds.subList(0, 3));
            topProductIds.addAll(accessoryIds.subList(0, 3));
            topProductIds.addAll(foodIds.subList(0, 3));

        } else {
            PageRequest page = PageRequest.of(0, 8, Sort.by(PagingAndSorting.DEFAULT_SORT_DIRECTION, "star")
                    .and(Sort.by(PagingAndSorting.DEFAULT_SORT_DIRECTION, "totalQuantityOrder")));
            List<ProductSummary> listsTemp = productSummaryRepository.findAll(page).getContent();
            if (listsTemp != null && listsTemp.size() != 0) {
                topProductIds = (ArrayList<Long>) listsTemp.stream().map(id -> id.getProduct().getId()).toList();
            }
        }
        List<Product> product = productRepository.findAllById(topProductIds);
        List<ProductCartDto> listDtos = this.listModelToDto(product);
        return listDtos;
    }

    @Override
    public double CalculateSaleOff(List<PromotionShop> listPromotion, double price) {
        return Math.round(promotionPriceService.CalculateSaleOff(listPromotion, price) * 100.0) / 100.0;
    }


    @Override
    public List<ProductCartDto> listModelToDto(List<Product> products) {
        if (products != null && products.size() != 0) {
            return products.stream()
                    .map(this::productToProductCart)
                    .collect(Collectors.toList());
        }
        return null;
    }

    @Override
    public ResponseEntity<?> retrieveProductById(Long id) {
        //must handle product if shop ban or ban will show out an message
        Optional<Product> product = productRepository.findById(id);
        if (product.isPresent()) {
            var productSummary = productSummaryRepository.findByProductIdAndProductStatusIn(id,
                    ProductStatusConstant.LIST_STATUS_GET_FOR_USER);
            if (productSummary.isPresent()) {
                ProductDto productDto = this.ProductToDto(product.get());

                List<String> listImages = MyUtils.toLists(product.get().getImgUrl(), ",");
                int numberSold = (int) productSummary.get().getTotalQuantityOrder();
                int numberReview = productSummary.get().getReviewTotal();

                ProductDetailWrapper productDetailWrapper = ProductDetailWrapper.builder()
                        .product(productDto)
                        .listImages(listImages)
                        .numberSold(numberSold)
                        .numberReview(numberReview).build();
                if(product.get().getShopOwner().getStatus().name().equalsIgnoreCase(ShopOwnerStatus.BAN.name())) {
                    return new ResponseEntity<>(productDetailWrapper, HttpStatus.valueOf(423));
                }else if(product.get().getStatus().name().equalsIgnoreCase(ProductStatus.BAN.name())){
                    return new ResponseEntity<>("This product got banned!", HttpStatus.valueOf(423));
                }else{
                    return ResponseEntity.ok(productDetailWrapper);
                }

            }else{
                return ResponseUtils.getErrorResponseNotFound(String.format("Product Summary not contain product with id %d", id));
            }
        }
        return new ResponseEntity<>(ResponseCode.NOT_FOUND_THIS_ID.toString(), HttpStatus.NOT_FOUND);
    }

    @Override
    public double CalculateDiscountedPrice(double price, double saleOff) {
        return promotionPriceService.CalculateDiscountedPrice(price, saleOff);
    }

    @Override
    public List<ProductDto> findProductByName(String name) {
        List<ProductDto> products = productRepository
                .findByNameLike("%" + name + "%")
                .get()
                .stream()
                .map(this::ProductToDto)
                .collect(Collectors.toList());
        return products;
    }

    @Override
    public ProductDto ProductToDto(Product product) {
        var productTemp = new ProductDto();
        if (product instanceof Bird) {
            productTemp = birdMapper.toDto((Bird) product);
        } else if (product instanceof Food) {
            productTemp = foodMapper.toDto((Food) product);
        } else if (product instanceof Accessory) {
            productTemp = accessoryMapper.toDto((Accessory) product);
        }
        productTemp.setImgUrl(MyUtils.toLists(product.getImgUrl(), ",").get(0));
        productTemp.setStar(this.CalculationRating(product.getOrderDetails()));
        productTemp.setDiscountRate(this.CalculateSaleOff(product.getPromotionShops(), productTemp.getPrice()));
        productTemp.setDiscountedPrice(promotionPriceService.CalculateDiscountedPrice(productTemp.getPrice(), productTemp.getDiscountRate()));
        productTemp.setCategoryId(Category.getCategoryIdByName(productTemp.getClass().getSimpleName()));
        return productTemp;
    }

    @Override
    public ResponseEntity<?> retrieveProductByListId(long[] ids) {
        var lists = productRepository.findByIdInAndQuantityGreaterThanAndStatusInAndShopOwner_StatusIn(Arrays.stream(ids).boxed().toList(),
                ProductStatusConstant.QUANTITY_PRODUCT_FOR_USER, ProductStatusConstant.LIST_STATUS_GET_FOR_USER, ShopOwnerConstant.STATUS_SHOP_PRODUCT_FOR_USER);
        if (lists.isPresent()) {
            return ResponseEntity.ok(lists.get().stream().map(this::productToProductCart).toList());
        }
        return new ResponseEntity<>(ResponseCode.NOT_FOUND_THIS_LIST_ID.toString(), HttpStatus.NOT_FOUND);
    }

    @Override
    public ResponseEntity<?> retrieveProductByShopId(long shopId, int pageNumber) {
        if (pageNumber > 0) {
            pageNumber = pageNumber - 1;
            PageRequest pageRequest = PageRequest.of(pageNumber, PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE,
                    Sort.by(PagingAndSorting.DEFAULT_SORT_DIRECTION, "lastUpDated"));

            Optional<Page<Product>> pageAble = productRepository.findByShopOwner_IdAndStatusIn(shopId,
                    ProductStatusConstant.LIST_STATUS_GET_FOR_USER, pageRequest);
            if (pageAble.isPresent()) {
                List<ProductDto> list = pageAble.get().stream()
                        .map(this::ProductToDto)
                        .toList();
                PageNumberWrapper<ProductDto> pageNumberWrapper = new PageNumberWrapper<>(list, pageAble.get().getTotalPages());
                return ResponseEntity.ok(pageNumberWrapper);
            } else {
                ErrorResponse error = new ErrorResponse(HttpStatus.NOT_FOUND.toString(),
                        "Not found product in shop.");
                return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
            }
        }
        ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.toString(),
                "Page number cannot less than 1");
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @Override
    public ProductShopDto productToProductShopDto(Product product) {
        if (product != null) {
            ProductShopDto productShopDto;
            if (product instanceof Bird) {
                productShopDto = new ProductShopDto<TypeBird>();
                productShopDto.setCategory(Category.getCategoryIdByName(new BirdDto().getClass().getSimpleName()));
                productShopDto.setType(((Bird) product).getTypeBird());
            } else if (product instanceof Food) {
                productShopDto = new ProductShopDto<TypeFood>();
                productShopDto.setCategory(Category.getCategoryIdByName(new FoodDto().getClass().getSimpleName()));
                productShopDto.setType(((Food) product).getTypeFood());
            } else if (product instanceof Accessory) {
                productShopDto = new ProductShopDto<TypeAccessory>();
                productShopDto.setCategory(Category.getCategoryIdByName(new AccessoryDto().getClass().getSimpleName()));
                productShopDto.setType(((Accessory) product).getTypeAccessory());
            } else {
                productShopDto = new ProductShopDto();
            }
            productShopDto.setId(product.getId());
            productShopDto.setShopId(product.getShopOwner().getId());
            productShopDto.setName(product.getName());
            productShopDto.setPrice(product.getPrice());
            productShopDto.setDiscountedPrice(CalculateDiscountedPrice(product.getPrice(), promotionPriceService.CalculateSaleOff(product.getPromotionShops(), product.getPrice())));
            productShopDto.setQuantity(product.getQuantity());
            productShopDto.setStatus(product.getStatus().getStatusCode());
            productShopDto.setCreateDate(product.getCreatedDate().getTime());
            productShopDto.setLastUpdate(product.getLastUpDated().getTime());
            //get product summary to take total order total review star
            var productSummary = productSummaryRepository.findByProductIdAndProductStatusInAndProduct_ShopOwner_StatusIn(product.getId(),
                    ProductStatusConstant.LIST_STATUS_GET_FOR_USER, ShopOwnerConstant.STATUS_SHOP_PRODUCT_FOR_USER);
            if (productSummary.isPresent()) {
                productShopDto.setTotalOrders(productSummary.get().getTotalQuantityOrder());
                productShopDto.setTotalReviews(productSummary.get().getReviewTotal());
                productShopDto.setStar(productSummary.get().getStar());
            }
//            productShopDto.setListDiscount(product.getPromotionShops().stream().map(promotionShopMapper::modelToDto).toList());
            return productShopDto;
        }
        return null;
    }

    @Override
    public ResponseEntity<?> updateListProductStatus(ChangeStatusListIdDto changeStatusListIdDto) {
        ProductUpdateStatus product = ProductUpdateStatus.getProductUpdateStatusEnum(changeStatusListIdDto.getStatus());
        try {
            if(product.getStatus() == -1) {
                var listOrderDetail = orderDetailRepository.findByProduct_IdIn(changeStatusListIdDto.getIds());
                if(listOrderDetail != null && listOrderDetail.size() >= 0) {
                    List<Long> orderId = listOrderDetail.stream().map(a -> a.getOrder().getId()).toList();
                    var list = orderRepository.findAllByStatusInAndIdIn(OrderStatusConstant.DELETE_PRODUCT_CHECK_ORDER_STATUS, orderId);
                    if(list.size() > 0) {
                        return ResponseUtils.getErrorResponseConflict("You cannot delete this product because you still have an order for this product");
                    }
                }
            }
            int numberStatusChange = productRepository.updateListProductStatus(product.getProductStatus(),
                    changeStatusListIdDto.getIds());
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("numberProductChange", numberStatusChange);
            jsonObject.addProperty("message", ResponseCode.UPDATE_LIST_PRODUCT_STATUS_SUCCESS.getMessage());
                return ResponseEntity.ok(jsonObject.toString());
        } catch (Exception e) {
//            return new ResponseEntity<>(ErrorResponse.builder().errorCode(ResponseCode.UPDATE_LIST_PRODUCT_STATUS_FAIL.getCode()+"")
//                    .errorMessage(ResponseCode.UPDATE_LIST_PRODUCT_STATUS_FAIL.getMessage()), HttpStatus.BAD_REQUEST);
            return new ResponseEntity<>(ResponseCode.UPDATE_LIST_PRODUCT_STATUS_FAIL.toString(), HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public ResponseEntity<?> updateListProductQuantity(List<ProductQuantityShopChangeDto> listProductChange) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        long shopId = shopOwnerService.getShopIdByEmail(email);
        int result = 0;
        ArrayList<Long> failId = new ArrayList<>();
        for (ProductQuantityShopChangeDto product : listProductChange) {
            if (product.getQuantity() >= 0) {
                result++;
                productRepository.updateListProductQuantity(product.getQuantity(), product.getId(), shopId);
            } else {
                failId.add(product.getId());
            }
        }
        if (failId.size() == 0) {
            return ResponseEntity.ok("Update success");
        } else {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("errorCode", "400");
            jsonObject.addProperty("message", "Update " + failId.size() + " fail");
            jsonObject.addProperty("listId", failId.toString());
            return new ResponseEntity<>(jsonObject.toString(), HttpStatus.BAD_REQUEST);

        }
    }

    @Override
    public ResponseEntity<?> filterAllProduct(
            ProductShopOwnerFilterDto productFilter,
            boolean isShopOwner, boolean isAdmin
    ) {
        Optional<Account> account = accountRepository.findByEmail(
                SecurityContextHolder.getContext().getAuthentication().getName()
        );
        Long shopId = null;
        if (account.isPresent() && isShopOwner) {
             shopId = account.get().getShopOwner().getId();
        }
        log.info("productFilter.getPageNumber() {}", productFilter.getPageNumber());
        if (productFilter.getPageNumber() > 0) {
            int pageNumber = productFilter.getPageNumber() - 1;
            PageRequest pageRequest = PageRequest.of(pageNumber, PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE);
            PageRequest pageRequestWithSort = null;
            if (productFilter.getSortDirection() != null
                    && !productFilter.getSortDirection().getSort().isEmpty()
                    && !productFilter.getSortDirection().getField().isEmpty()
            ) {
                log.info("go pageRequestWithSort---------------------");
                if (!SortProductColumn.checkField(productFilter.getSortDirection().getField())) {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                            .errorMessage("Not found this field in sort direction.")
                            .build();
                    return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
                }
                if (productFilter.getSortDirection().getSort().toUpperCase().equals(Sort.Direction.ASC.name())) {
                    pageRequestWithSort = getPageRequest(productFilter, pageNumber, Sort.Direction.ASC);
                } else if (productFilter.getSortDirection().getSort().toUpperCase().equals(Sort.Direction.DESC.name())) {
                    pageRequestWithSort = getPageRequest(productFilter, pageNumber, Sort.Direction.DESC);
                } else {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                            .errorMessage("Not found this direction.")
                            .build();
                    return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
                }
            }

//            {"category":1,"productSearchInfo":{"field":"","value":"","operator":""},"sortDirection":{"field":"","sort":""},"pageNumber":1}
            if (
                    productFilter.getProductSearchInfo().getField().isEmpty()
                            && productFilter.getProductSearchInfo().getValue().isEmpty()
                            && productFilter.getProductSearchInfo().getOperator().isEmpty()
                            && productFilter.getSortDirection().getField().isEmpty()
                            && productFilter.getSortDirection().getSort().isEmpty()
            ) {
                log.info("all no");
                pageRequestWithSort = PageRequest.of(
                        pageNumber,
                        PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE,
                        Sort.by(Sort.Direction.DESC,
                                SortProductColumn.LAST_UPDATE.getColumn()
                        )
                );
                return filterAllProductAllFieldEmpty(productFilter, shopId, pageRequestWithSort, isShopOwner, isAdmin);
            } else if (
                    productFilter.getProductSearchInfo().getField().isEmpty()
                            && productFilter.getProductSearchInfo().getValue().isEmpty()
                            && productFilter.getProductSearchInfo().getOperator().isEmpty()
                            && !productFilter.getSortDirection().getField().isEmpty()
                            && !productFilter.getSortDirection().getSort().isEmpty()
            ) {
                log.info("with sort");
                return filterAllProductAllFieldEmpty(productFilter, shopId, pageRequestWithSort, isShopOwner, isAdmin);
            }


            if (
                    productFilter.getProductSearchInfo().getField().equals(FieldProductTable.ID.getField())
                            && productFilter.getProductSearchInfo().getValue() != null
            ) {
                if (productFilter.getProductSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterProductByIdEqual(productFilter, shopId, pageRequest, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    productFilter.getProductSearchInfo().getField().equals(FieldProductTable.SHOP_ID.getField())
                            && !productFilter.getProductSearchInfo().getValue().isEmpty()
                            && productFilter.getSortDirection().getField().isEmpty()
                            && productFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (productFilter.getProductSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterProductByShopIdEqual(productFilter, pageRequest, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    productFilter.getProductSearchInfo().getField().equals(FieldProductTable.SHOP_ID.getField())
                    && !productFilter.getProductSearchInfo().getValue().isEmpty()
            ) {
                if (productFilter.getProductSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterProductByShopIdEqual(productFilter, pageRequest, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    productFilter.getProductSearchInfo().getField().equals(FieldProductTable.NAME.getField())
                            && !productFilter.getProductSearchInfo().getValue().isEmpty()
                            && productFilter.getSortDirection().getField().isEmpty()
                            && productFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (productFilter.getProductSearchInfo().getOperator().equals(Operator.CONTAIN.getOperator())) {
                    return filterProductByNameLike(productFilter, shopId, pageRequest, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    productFilter.getProductSearchInfo().getField().equals(FieldProductTable.NAME.getField())
                            && !productFilter.getProductSearchInfo().getValue().isEmpty()
            ) {
                if (productFilter.getProductSearchInfo().getOperator().equals(Operator.CONTAIN.getOperator())) {
                    return filterProductByNameLike(productFilter, shopId, pageRequestWithSort, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    productFilter.getProductSearchInfo().getField().equals(FieldProductTable.TYPE.getField())
                            && !productFilter.getProductSearchInfo().getValue().isEmpty()
                            && productFilter.getSortDirection().getField().isEmpty()
                            && productFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (productFilter.getProductSearchInfo().getOperator().equals(Operator.CONTAIN.getOperator())) {
                    return filterProductByTypeNameLike(productFilter, shopId, pageRequest, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    productFilter.getProductSearchInfo().getField().equals(FieldProductTable.TYPE.getField())
                            && !productFilter.getProductSearchInfo().getValue().isEmpty()
            ) {
                if (productFilter.getProductSearchInfo().getOperator().equals(Operator.CONTAIN.getOperator())) {
                    return filterProductByTypeNameLike(productFilter, shopId, pageRequestWithSort, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    productFilter.getProductSearchInfo().getField().equals(FieldProductTable.PRICE.getField())
                            && !productFilter.getProductSearchInfo().getValue().isEmpty()
                            && productFilter.getSortDirection().getField().isEmpty()
                            && productFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (productFilter.getProductSearchInfo().getOperator().equals(Operator.GREATER_THAN_OR_EQUAL.getOperator())) {
                    return filterProductByPriceGreaterThanOrEqual(productFilter, shopId, pageRequest, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    productFilter.getProductSearchInfo().getField().equals(FieldProductTable.PRICE.getField())
                            && !productFilter.getProductSearchInfo().getValue().isEmpty()
            ) {
                if (productFilter.getProductSearchInfo().getOperator().equals(Operator.GREATER_THAN_OR_EQUAL.getOperator())) {
                    return filterProductByPriceGreaterThanOrEqual(productFilter, shopId, pageRequestWithSort, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    productFilter.getProductSearchInfo().getField().equals(FieldProductTable.DISCOUNTED_PRICE.getField())
                            && !productFilter.getProductSearchInfo().getValue().isEmpty()
                            && productFilter.getSortDirection().getField().isEmpty()
                            && productFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (productFilter.getProductSearchInfo().getOperator().equals(Operator.GREATER_THAN_OR_EQUAL.getOperator())) {
                    return filterProductByDiscountedPriceGreaterThanOrEqual(productFilter, shopId, pageRequest, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    productFilter.getProductSearchInfo().getField().equals(FieldProductTable.DISCOUNTED_PRICE.getField())
                            && !productFilter.getProductSearchInfo().getValue().isEmpty()
            ) {
                if (productFilter.getProductSearchInfo().getOperator().equals(Operator.GREATER_THAN_OR_EQUAL.getOperator())) {
                    return filterProductByDiscountedPriceGreaterThanOrEqual(productFilter, shopId, pageRequestWithSort, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    productFilter.getProductSearchInfo().getField().equals(FieldProductTable.STATUS.getField())
                            && !productFilter.getProductSearchInfo().getValue().isEmpty()
                            && productFilter.getSortDirection().getField().isEmpty()
                            && productFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (productFilter.getProductSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterProductByStatusEqual(productFilter, shopId, pageRequest, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    productFilter.getProductSearchInfo().getField().equals(FieldProductTable.STATUS.getField())
                            && !productFilter.getProductSearchInfo().getValue().isEmpty()
            ) {
                if (productFilter.getProductSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterProductByStatusEqual(productFilter, shopId, pageRequestWithSort, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else {
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                        .errorMessage("Product filter is not correct.")
                        .build();
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
            }
        } else {
            ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.toString(),
                    "Page number cannot less than 1");
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }
    }

    private ResponseEntity<?> filterProductByShopIdEqual(
            ProductShopOwnerFilterDto productFilter,
            PageRequest pageRequest, boolean isAdmin
    ) {
        if (productFilter.getCategory() == 1) {
            Optional<Page<Bird>> birds = Optional.empty();
             if (isAdmin) {
                birds = birdRepository.findAllByShopOwner_IdAndStatusIn(
                        Long.valueOf(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (birds.isPresent()) {
                return getPageNumberWrapperBirds(birds);
            }
        } else if (productFilter.getCategory() == 2) {
            Optional<Page<Food>> foods = Optional.empty();
            if (isAdmin) {
                foods = foodRepository.findAllByShopOwner_IdAndStatusIn(
                        Long.valueOf(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (foods.isPresent()) {
                return getPageNumberWrapperFoods(foods);
            }
        } else if (productFilter.getCategory() == 3) {
            Optional<Page<Accessory>> accessories = Optional.empty();
            if (isAdmin) {
                accessories = accessoryRepository.findAllByShopOwner_IdAndStatusIn(
                        Long.valueOf(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (accessories.isPresent()) {
                return getPageNumberWrapperAccessories(accessories);
            }
        } else {
            return getErrorResponseNotFoundCategory();
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found this shop id.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @Override
    public ResponseEntity<?> getProductDetailForShop(long productId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        long shopId = shopOwnerService.getShopIdByEmail(email);
        var product = productRepository.findByIdAndStatusInAndShopOwner_Id(productId,
                ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER, shopId);
        int category = 0;
        if (product.isPresent()) {
            JsonObject json = new JsonObject();
            // Create a Gson instance
            Gson gson = new Gson();

            // basicForm
            JsonObject basicFormData = new JsonObject();
            basicFormData.addProperty("id", product.get().getId());
            basicFormData.addProperty("name", product.get().getName());

            // feature
            JsonObject feature = new JsonObject();

            // detailsForm
            JsonObject detailsFormData = new JsonObject();

            // salesForm
            JsonObject salesFormData = new JsonObject();

            Product pro = null;
            TypeDto typeDto = new TypeDto();
            List<TagDto> tagDtos = new ArrayList<>();
            if (product.get() instanceof Bird) {
                category = Category.getCategoryIdByName(new BirdDto().getClass().getSimpleName());
                typeDto = typeMapper.modelToDto(((Bird) product.get()).getTypeBird());
                tagDtos = ((Bird) product.get()).getTags().stream().map(tagMapper::modelToDto).toList();
            } else if (product.get() instanceof Food) {
                category = Category.getCategoryIdByName(new FoodDto().getClass().getSimpleName());
                typeDto = typeMapper.modelToDto(((Food) product.get()).getTypeFood());
                tagDtos = ((Food) product.get()).getTags().stream().map(tagMapper::modelToDto).toList();
            } else if (product.get() instanceof Accessory) {
                category = Category.getCategoryIdByName(new AccessoryDto().getClass().getSimpleName());
                typeDto = typeMapper.modelToDto(((Accessory) product.get()).getTypeAccessory());
                tagDtos = ((Accessory) product.get()).getTags().stream().map(tagMapper::modelToDto).toList();
            }

            basicFormData.addProperty("category", category);
            detailsFormData.addProperty("description", product.get().getDescription());
            //set type object
//            String jsonType = gson.toJson(typeDto);
//            JsonObject type = JsonParser.parseString(jsonType).getAsJsonObject();
            detailsFormData.addProperty("type", typeDto.getId());
            String jsonTag = gson.toJson(tagDtos);
            JsonArray jsonArrayTag = JsonParser.parseString(jsonTag).getAsJsonArray();
            detailsFormData.add("tags", jsonArrayTag);

            salesFormData.addProperty("price", product.get().getPrice());
            salesFormData.addProperty("quantity", product.get().getQuantity());

            //set list promotion
            List<PromotionShop> promotionShopTemp = promotionShopRepository.findByProductIdAndEndDate(product.get().getId(), new Date()).get();
            log.info("here is list promotion {}", promotionShopTemp.size());
            List<PromotionShopDto> listPromotion = promotionShopTemp.stream()
                    .map(promotionShopMapper::modelToDto).toList();
            String jsonPromotionTemp = gson.toJson(listPromotion);
            JsonArray voucherArray = JsonParser.parseString(jsonPromotionTemp).getAsJsonArray();
            salesFormData.add("voucher", voucherArray);

            json.add("basicForm", basicFormData);
            json.add("feature", this.getFeatureBaseOnInstance(product.get()));
            json.add("detailsForm", detailsFormData);
            json.add("salesForm", salesFormData);

            //images
            List<String> images = Arrays.stream(product.get().getImgUrl().split(",")).toList();
            String listImages = gson.toJson(images);
            JsonArray imageArray = JsonParser.parseString(listImages).getAsJsonArray();
            json.add("listImages", imageArray);
            String video = product.get().getVideoUrl();
            if(product.get().getVideoUrl() == null || product.get().getVideoUrl().equals("NULL") ||
                    product.get().getVideoUrl().isEmpty()) {
                video = "";
            }
            json.addProperty("video", video);
            String jsonString = json.toString();
            return ResponseEntity.ok(jsonString);
        } else {
            return new ResponseEntity<>((ErrorResponse.builder().errorCode("400")
                    .errorMessage("Not found this product!").build()), HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    @Transactional
    public ResponseEntity<?> updateProduct(
            List<MultipartFile> multipartImgList,
            MultipartFile multipartVideo,
            ProductUpdateDto productUpdate) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Optional<Account> account = accountRepository.findByEmail(authentication.getName());
        if (productUpdate.getBasicForm().getCategory() == Category.BIRD.getCategoryId()) {
            Bird bird = birdRepository.findByIdAndShopOwner(
                    productUpdate.getBasicForm().getId(), account.get().getShopOwner()
            );
            if (bird != null) {
                List<Long> tagIds = productUpdate.getDetailsForm().getTags().stream()
                        .map(TagDto::getId)
                        .toList();
                List<Tag> tags = tagRepository.findByIdIn(tagIds);
                List<Long> promotionShopIds = productUpdate.getSalesForm().getVoucher().stream()
                        .map(PromotionShopDto::getId)
                        .toList();
                promotionShopIds.forEach(pro -> log.info("pr {}", pro));
                List<PromotionShop> promotionShops = promotionShopRepository.findAllPromotionShopByIdIn(promotionShopIds);
                bird.setName(productUpdate.getBasicForm().getName());
                bird.setAge(productUpdate.getFeature().getAge());
                bird.setGender(productUpdate.getFeature().getGender());
                bird.setColor(productUpdate.getFeature().getColor());
                bird.setDescription(productUpdate.getDetailsForm().getDescription());
                bird.setTypeBird(typeBirdRepository.findById(productUpdate.getDetailsForm().getTypeId()).get());
                if (tags.size() > 0) {
                    bird.setTags(tags);
                }
                bird.setPrice(productUpdate.getSalesForm().getPrice());
                bird.setQuantity(productUpdate.getSalesForm().getQuantity());
                log.info("promotionShops.size() {}", promotionShops.size());
                if (promotionShops.size() > 0) {
                    bird.setPromotionShops(promotionShops);
                } else {
                    bird.setPromotionShops(null);
                }
                List<String> urlList = Arrays.asList(bird.getImgUrl().split(","));
                List<String> listImagesRemove = productUpdate.getListImages();
                List<String> updateUrlList = new ArrayList<>();
                //Remove image
                if (listImagesRemove.size() > 0) {
                    ResponseEntity<ErrorResponse> errorNotFoundLinkImage = getErrorResponseNotFoundLinkImage(urlList, listImagesRemove);
                    if (errorNotFoundLinkImage != null)
                        return errorNotFoundLinkImage;
                    //remove in DB
                    listImagesRemove.forEach(image -> log.info("image remove {}", image));
                    urlList.forEach(image -> log.info("image {}", image));
                    for (int i = 0; i < urlList.size(); i++) {
                        if (!listImagesRemove.contains(urlList.get(i))) {
                            updateUrlList.add(urlList.get(i));
                        }
                    }
                    updateUrlList.forEach(image -> log.info("image after {}", image));
                    //remove in S3
                    ResponseEntity<ErrorResponse> errorResponse = removeListImageInS3(listImagesRemove);
                    if (errorResponse != null) {
                        return errorResponse;
                    }
                } else {
                    for (String urlImage : urlList) {
                        updateUrlList.add(urlImage);
                    }
                }
                String originUrl = appProperties.getS3().getUrl();
                //Add new image
                if (multipartImgList != null && !multipartImgList.isEmpty()) {
                    for (MultipartFile multipartFile : multipartImgList) {
                        String newFilename = FileNameUtils.getNewImageFileName(multipartFile);
                        updateUrlList.add(originUrl + newFilename);
                        try {
                            S3Utils.uploadFile(newFilename, multipartFile.getInputStream());
                        } catch (Exception ex) {
                            ErrorResponse errorResponse = ErrorResponse.builder()
                                    .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                                    .errorMessage("Upload file fail")
                                    .build();
                            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
                        }
                    }
                }

                updateUrlList.forEach(image -> log.info("image after add new {}", image));

                String imgUrl = updateUrlList.stream()
                        .collect(Collectors.joining(","));
                bird.setImgUrl(imgUrl);
                String oldVideoUrl = bird.getVideoUrl();
                //Only delete video
                if (productUpdate.isDeleteVideo() && multipartVideo == null) {
                    if (oldVideoUrl != null && !oldVideoUrl.isEmpty()) {
                        ResponseEntity<ErrorResponse> errorResponse = removeVideoInS3(oldVideoUrl);
                        if (errorResponse != null) {
                            return errorResponse;
                        }
                    }
                    bird.setVideoUrl(null);
                }
                //Add new video
                if (multipartVideo != null && !multipartVideo.isEmpty()) {
                    String newFilename = FileNameUtils.getNewVideoFileName(multipartVideo);
                    String urlVideo = originUrl + newFilename;
                    try {
                        S3Utils.uploadFile(newFilename, multipartVideo.getInputStream());
                        bird.setVideoUrl(urlVideo);
                    } catch (Exception ex) {
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                                .errorMessage("Upload file fail")
                                .build();
                        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
                    }
                    if (oldVideoUrl != null && !oldVideoUrl.isEmpty()) {
                        ResponseEntity<ErrorResponse> errorResponse = removeVideoInS3(oldVideoUrl);
                        if (errorResponse != null) {
                            return errorResponse;
                        }
                    }
                }
                birdRepository.save(bird);
                return this.getProductDetailForShop(bird.getId());
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Bird not found.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else if (productUpdate.getBasicForm().getCategory() == Category.FOOD.getCategoryId()) {
            Food food = foodRepository.findByIdAndShopOwner(
                    productUpdate.getBasicForm().getId(), account.get().getShopOwner()
            );
            if (food != null) {
                List<Long> tagIds = productUpdate.getDetailsForm().getTags().stream()
                        .map(TagDto::getId)
                        .toList();
                List<Tag> tags = tagRepository.findByIdIn(tagIds);
                List<Long> promotionShopIds = productUpdate.getSalesForm().getVoucher().stream()
                        .map(PromotionShopDto::getId)
                        .toList();
                List<PromotionShop> promotionShops = promotionShopRepository.findAllPromotionShopByIdIn(promotionShopIds);
                food.setName(productUpdate.getBasicForm().getName());
                food.setWeight(productUpdate.getFeature().getWeight());
                food.setDescription(productUpdate.getDetailsForm().getDescription());
                food.setTypeFood(typeFoodRepository.findById(productUpdate.getDetailsForm().getTypeId()).get());
                if (tags.size() > 0) {
                    food.setTags(tags);
                }
                food.setPrice(productUpdate.getSalesForm().getPrice());
                food.setQuantity(productUpdate.getSalesForm().getQuantity());
                if (promotionShops.size() > 0) {
                    food.setPromotionShops(promotionShops);
                } else {
                    food.setPromotionShops(null);
                }
                List<String> urlList = Arrays.asList(food.getImgUrl().split(","));
                List<String> listImagesRemove = productUpdate.getListImages();
                List<String> updateUrlList = new ArrayList<>();
                //Remove image
                if (listImagesRemove.size() > 0) {
                    ResponseEntity<ErrorResponse> errorNotFoundLinkImage = getErrorResponseNotFoundLinkImage(urlList, listImagesRemove);
                    if (errorNotFoundLinkImage != null)
                        return errorNotFoundLinkImage;
                    //remove in DB
                    listImagesRemove.forEach(image -> log.info("image remove {}", image));
                    urlList.forEach(image -> log.info("image {}", image));
                    for (int i = 0; i < urlList.size(); i++) {
                        if (!listImagesRemove.contains(urlList.get(i))) {
                            updateUrlList.add(urlList.get(i));
                        }
                    }
                    updateUrlList.forEach(image -> log.info("image after {}", image));
                    //remove in S3
                    ResponseEntity<ErrorResponse> errorResponse = removeListImageInS3(listImagesRemove);
                    if (errorResponse != null) {
                        return errorResponse;
                    }
                } else {
                    for (String urlImage : urlList) {
                        updateUrlList.add(urlImage);
                    }
                }
                String originUrl = appProperties.getS3().getUrl();
                //Add new image
                if (multipartImgList != null && !multipartImgList.isEmpty()) {
                    for (MultipartFile multipartFile : multipartImgList) {
                        String newFilename = FileNameUtils.getNewImageFileName(multipartFile);
                        updateUrlList.add(originUrl + newFilename);
                        try {
                            S3Utils.uploadFile(newFilename, multipartFile.getInputStream());
                        } catch (Exception ex) {
                            ErrorResponse errorResponse = ErrorResponse.builder()
                                    .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                                    .errorMessage("Upload file fail")
                                    .build();
                            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
                        }
                    }
                }
                String imgUrl = updateUrlList.stream()
                        .collect(Collectors.joining(","));
                food.setImgUrl(imgUrl);
                String oldVideoUrl = food.getVideoUrl();
                //Only delete video
                if (productUpdate.isDeleteVideo() && multipartVideo == null) {
                    if (oldVideoUrl != null && !oldVideoUrl.isEmpty()) {
                        ResponseEntity<ErrorResponse> errorResponse = removeVideoInS3(oldVideoUrl);
                        if (errorResponse != null) {
                            return errorResponse;
                        }
                    }
                    food.setVideoUrl(null);
                }
                //Add new video
                if (multipartVideo != null && !multipartVideo.isEmpty()) {
                    String newFilename = FileNameUtils.getNewVideoFileName(multipartVideo);
                    String urlVideo = originUrl + newFilename;
                    try {
                        S3Utils.uploadFile(newFilename, multipartVideo.getInputStream());
                        food.setVideoUrl(urlVideo);
                    } catch (Exception ex) {
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                                .errorMessage("Upload file fail")
                                .build();
                        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
                    }
                    if (oldVideoUrl != null && !oldVideoUrl.isEmpty()) {
                        ResponseEntity<ErrorResponse> errorResponse = removeVideoInS3(oldVideoUrl);
                        if (errorResponse != null) {
                            return errorResponse;
                        }
                    }
                }
                foodRepository.save(food);
                return this.getProductDetailForShop(food.getId());
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Food not found.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else if (productUpdate.getBasicForm().getCategory() == Category.ACCESSORY.getCategoryId()) {
            Accessory accessory = accessoryRepository.findByIdAndShopOwner(
                    productUpdate.getBasicForm().getId(), account.get().getShopOwner()
            );
            if (accessory != null) {
                List<Long> tagIds = productUpdate.getDetailsForm().getTags().stream()
                        .map(TagDto::getId)
                        .toList();
                List<Tag> tags = tagRepository.findByIdIn(tagIds);
                List<Long> promotionShopIds = productUpdate.getSalesForm().getVoucher().stream()
                        .map(PromotionShopDto::getId)
                        .toList();
                List<PromotionShop> promotionShops = promotionShopRepository.findAllPromotionShopByIdIn(promotionShopIds);
                accessory.setName(productUpdate.getBasicForm().getName());
                accessory.setOrigin(productUpdate.getFeature().getOrigin());
                accessory.setDescription(productUpdate.getDetailsForm().getDescription());
                accessory.setTypeAccessory(typeAccessoryRepository.findById(productUpdate.getDetailsForm().getTypeId()).get());
                if (tags.size() > 0) {
                    accessory.setTags(tags);
                }
                accessory.setPrice(productUpdate.getSalesForm().getPrice());
                accessory.setQuantity(productUpdate.getSalesForm().getQuantity());
                if (promotionShops.size() > 0) {
                    accessory.setPromotionShops(promotionShops);
                } else {
                    accessory.setPromotionShops(null);
                }
                List<String> urlList = Arrays.asList(accessory.getImgUrl().split(","));
                List<String> listImagesRemove = productUpdate.getListImages();
                List<String> updateUrlList = new ArrayList<>();
                //Remove image
                if (listImagesRemove.size() > 0) {
                    ResponseEntity<ErrorResponse> errorNotFoundLinkImage = getErrorResponseNotFoundLinkImage(urlList, listImagesRemove);
                    if (errorNotFoundLinkImage != null)
                        return errorNotFoundLinkImage;
                    //remove in DB
                    listImagesRemove.forEach(image -> log.info("image remove {}", image));
                    urlList.forEach(image -> log.info("image {}", image));
                    for (int i = 0; i < urlList.size(); i++) {
                        if (!listImagesRemove.contains(urlList.get(i))) {
                            updateUrlList.add(urlList.get(i));
                        }
                    }
                    updateUrlList.forEach(image -> log.info("image after {}", image));
                    //remove in S3
                    ResponseEntity<ErrorResponse> errorResponse = removeListImageInS3(listImagesRemove);
                    if (errorResponse != null) {
                        return errorResponse;
                    }
                } else {
                    for (String urlImage : urlList) {
                        updateUrlList.add(urlImage);
                    }
                }
                String originUrl = appProperties.getS3().getUrl();
                //Add new image
                if (multipartImgList != null && !multipartImgList.isEmpty()) {
                    for (MultipartFile multipartFile : multipartImgList) {
                        String newFilename = FileNameUtils.getNewImageFileName(multipartFile);
                        updateUrlList.add(originUrl + newFilename);
                        try {
                            S3Utils.uploadFile(newFilename, multipartFile.getInputStream());
                        } catch (Exception ex) {
                            ErrorResponse errorResponse = ErrorResponse.builder()
                                    .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                                    .errorMessage("Upload file fail")
                                    .build();
                            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
                        }
                    }
                }
                String imgUrl = updateUrlList.stream()
                        .collect(Collectors.joining(","));
                accessory.setImgUrl(imgUrl);

                String oldVideoUrl = accessory.getVideoUrl();
                //Only delete video
                if (productUpdate.isDeleteVideo() && multipartVideo == null) {
                    if (oldVideoUrl != null && !oldVideoUrl.isEmpty()) {
                        ResponseEntity<ErrorResponse> errorResponse = removeVideoInS3(oldVideoUrl);
                        if (errorResponse != null) {
                            return errorResponse;
                        }
                    }
                    accessory.setVideoUrl(null);
                }
                //Add new video
                if (multipartVideo != null && !multipartVideo.isEmpty()) {
                    String newFilename = FileNameUtils.getNewVideoFileName(multipartVideo);
                    String urlVideo = originUrl + newFilename;
                    try {
                        S3Utils.uploadFile(newFilename, multipartVideo.getInputStream());
                        accessory.setVideoUrl(urlVideo);
                    } catch (Exception ex) {
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                                .errorMessage("Upload file fail")
                                .build();
                        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
                    }
                    if (oldVideoUrl != null && !oldVideoUrl.isEmpty()) {
                        ResponseEntity<ErrorResponse> errorResponse = removeVideoInS3(oldVideoUrl);
                        if (errorResponse != null) {
                            return errorResponse;
                        }
                    }
                }
                accessoryRepository.save(accessory);
                return this.getProductDetailForShop(accessory.getId());
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Accessory not found.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else {
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Not found this category id.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<?> getProductRelevantBaseOnId(long productId) {
        var product = productRepository.findById(productId);
        if(product.isPresent()) {
            PageRequest pageRequest = PageRequest.of(0, PagingAndSorting.DEFAULT_PAGE_SIZE);
            List<Product> listProduct = new ArrayList<>();
            List<Long> tagid;
            if (product.get() instanceof Bird) {
                tagid = ((Bird) product.get()).getTags().stream().map(a -> a.getId()).toList();
                listProduct = birdRepository.findDistinctBirdsByTypeAndTagsSortedByTotalQuantity(((Bird) product.get()).getTypeBird().getId(),
                        tagid, ProductStatusConstant.LIST_STATUS_GET_FOR_USER, ShopOwnerConstant.STATUS_SHOP_PRODUCT_FOR_USER ,pageRequest );
            }
            else if (product.get() instanceof Food) {
                tagid = ((Food) product.get()).getTags().stream().map(a -> a.getId()).toList();
                listProduct = foodRepository.findDistinctBirdsByTypeAndTagsSortedByTotalQuantity(((Food) product.get()).getTypeFood().getId(),
                        tagid, ProductStatusConstant.LIST_STATUS_GET_FOR_USER, ShopOwnerConstant.STATUS_SHOP_PRODUCT_FOR_USER ,pageRequest );
            } else {
                tagid = ((Accessory) product.get()).getTags().stream().map(a -> a.getId()).toList();
                listProduct = accessoryRepository.findDistinctBirdsByTypeAndTagsSortedByTotalQuantity(((Accessory) product.get()).getTypeAccessory().getId(),
                        tagid, ProductStatusConstant.LIST_STATUS_GET_FOR_USER, ShopOwnerConstant.STATUS_SHOP_PRODUCT_FOR_USER ,pageRequest );
            }

            List<ProductCartDto> list =  listProduct.stream().map(this::productToProductCart).toList();
            return ResponseEntity.ok(list);
        }
        return null;
    }

    @Override
    public ResponseEntity<?> retrieveProductByShopidAndTagId(long shopId, long[] tagId) {
        List<Tag> tags = new ArrayList<>();
        Arrays.stream(tagId).forEach(id -> {
            Tag tag = new Tag();
            tag.setId(id);
            tags.add(tag);
        });
        List<Product> products = new ArrayList<>();
        products.addAll(accessoryRepository.findByTagsInAndShopOwner_IdAndStatusIn(tags, shopId, ProductStatusConstant.LIST_STATUS_GET_FOR_USER).get());
        products.addAll(foodRepository.findByTagsInAndShopOwner_IdAndStatusIn(tags, shopId, ProductStatusConstant.LIST_STATUS_GET_FOR_USER).get());
        products.addAll(birdRepository.findByTagsInAndShopOwner_IdAndStatusIn(tags, shopId, ProductStatusConstant.LIST_STATUS_GET_FOR_USER).get());
        List<ProductCartDto> result = products.stream().map(pro -> this.productToProductCart(pro)).toList();
        return ResponseEntity.ok(result);
    }


    private ResponseEntity<ErrorResponse> getErrorResponseNotFoundLinkImage(List<String> urlList, List<String> listImagesRemove) {
        for (String imgRemove : listImagesRemove) {
            if (!urlList.contains(imgRemove)) {
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                        .errorMessage("No find image link to remove.")
                        .build();
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
            }
        }
        return null;
    }

    private ResponseEntity<ErrorResponse> removeVideoInS3(String videoRemove) {
        String originUrl = appProperties.getS3().getUrl();
        try {
            S3Utils.deleteFile(videoRemove.substring(originUrl.length()));
        } catch (Exception ex) {
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                    .errorMessage("Remove file fail")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }
        return null;
    }

    private ResponseEntity<ErrorResponse> removeListImageInS3(List<String> listImagesRemove) {
        String originUrl = appProperties.getS3().getUrl();
        for (String removeImg : listImagesRemove) {
            removeImg = removeImg.substring(originUrl.length());
            try {
                S3Utils.deleteFile(removeImg);
            } catch (Exception ex) {
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                        .errorMessage("Remove file fail")
                        .build();
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
            }
        }
        return null;
    }

    private <T extends Product> JsonObject getFeatureBaseOnInstance(T product) {
        JsonObject feature = new JsonObject();
        if (product instanceof Bird) {
            feature.addProperty("age", ((Bird) product).getAge());
            feature.addProperty("gender", ((Bird) product).getGender().name());
            feature.addProperty("color", ((Bird) product).getColor());
        } else if (product instanceof Accessory) {
            feature.addProperty("origin", ((Accessory) product).getOrigin());
        } else if (product instanceof Food) {
            feature.addProperty("weight", ((Food) product).getWeight());
        }
        return feature;
    }

    private ResponseEntity<?> filterAllProductAllFieldEmpty(
            ProductShopOwnerFilterDto productFilter, Long shopId,
            PageRequest pageRequest, boolean isShopOwner, boolean isAdmin
    ) {
        if (productFilter.getCategory() == 1) {
            Optional<Page<Bird>> birds = Optional.empty();
            if (isShopOwner) {
                birds = birdRepository.findAllByShopOwner_IdAndStatusIn(
                        shopId,
                        ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER,
                        pageRequest
                );
            } else if (isAdmin) {
                birds = birdRepository.findAllByStatusIn(
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (birds.isPresent()) {
                return getPageNumberWrapperBirds(birds);
            } else {
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                        .errorMessage("Not found bird.")
                        .build();
                return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
            }
        } else if (productFilter.getCategory() == 2) {
            Optional<Page<Food>> foods = Optional.empty();
            if (isShopOwner) {
                foods = foodRepository.findAllByShopOwner_IdAndStatusIn(
                        shopId,
                        ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER,
                        pageRequest
                );
            } else if (isAdmin) {
                foods = foodRepository.findAllByStatusIn(
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (foods.isPresent()) {
                return getPageNumberWrapperFoods(foods);
            } else {
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                        .errorMessage("Not found food.")
                        .build();
                return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
            }
        } else if (productFilter.getCategory() == 3) {
            Optional<Page<Accessory>> accessories = Optional.empty();
            if (isShopOwner) {
                accessories = accessoryRepository.findAllByShopOwner_IdAndStatusIn(
                        shopId,
                        ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER,
                        pageRequest
                );
            } else if (isAdmin) {
                accessories = accessoryRepository.findAllByStatusIn(
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (accessories.isPresent()) {
                return getPageNumberWrapperAccessories(accessories);
            } else {
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                        .errorMessage("Not found accessory.")
                        .build();
                return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
            }
        } else {
            return getErrorResponseNotFoundCategory();
        }
    }

    private PageRequest getPageRequest(ProductShopOwnerFilterDto productFilter, int pageNumber, Sort.Direction sortDirection) {
        PageRequest pageRequestWithSort = null;
        if (productFilter.getCategory() == 1) {
            if (productFilter.getSortDirection().getField().equals(SortProductColumn.TYPE_BIRD.getField())) {
                pageRequestWithSort = PageRequest.of(
                        pageNumber,
                        PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE,
                        Sort.by(sortDirection,

                                SortProductColumn.TYPE_BIRD.getColumn())
                );
            } else {
                log.info("go here page 1");
                pageRequestWithSort = PageRequest.of(
                        pageNumber,
                        PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE,
                        Sort.by(sortDirection,
                                SortProductColumn.getColumnByField(productFilter.getSortDirection().getField())
                        )
                );
            }
        } else if (productFilter.getCategory() == 2) {
            if (productFilter.getSortDirection().getField().equals(SortProductColumn.TYPE_FOOD.getField())) {
                pageRequestWithSort = PageRequest.of(
                        pageNumber,
                        PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE,
                        Sort.by(sortDirection,
                                SortProductColumn.TYPE_FOOD.getColumn())
                );
            } else {
                log.info("go here page 2");
                pageRequestWithSort = PageRequest.of(
                        pageNumber,
                        PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE,
                        Sort.by(sortDirection,
                                SortProductColumn.getColumnByField(productFilter.getSortDirection().getField())
                        )
                );
            }
        } else if (productFilter.getCategory() == 3) {
            if (productFilter.getSortDirection().getField().equals(SortProductColumn.TYPE_ACCESSORY.getField())) {
                pageRequestWithSort = PageRequest.of(
                        pageNumber,
                        PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE,
                        Sort.by(sortDirection,
                                SortProductColumn.TYPE_ACCESSORY.getColumn())
                );
            } else {
                log.info("go here page 3");
                pageRequestWithSort = PageRequest.of(
                        pageNumber,
                        PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE,
                        Sort.by(sortDirection,
                                SortProductColumn.getColumnByField(productFilter.getSortDirection().getField())
                        )
                );
            }
        }
        return pageRequestWithSort;
    }

    private ResponseEntity<?> filterProductByStatusEqual(
            ProductShopOwnerFilterDto productFilter, Long shopId,
            PageRequest pageRequest, boolean isShopOwner, boolean isAdmin
    ) {
        List<ProductStatus> productStatuses;
        if (Integer.parseInt(productFilter.getProductSearchInfo().getValue()) == 9 && isShopOwner) {
            productStatuses = ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER;
        } else if (Integer.parseInt(productFilter.getProductSearchInfo().getValue()) == 9 && isAdmin) {
            productStatuses = ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN;
        } else {
            productStatuses = Arrays.asList(ProductUpdateStatus.getProductUpdateStatusEnum(
                    Integer.parseInt(productFilter.getProductSearchInfo().getValue())
            ).getProductStatus());
        }
        if (productFilter.getCategory() == 1) {
            Optional<Page<Bird>> birds = Optional.empty();
            if (isShopOwner) {
                birds = birdRepository.findAllByShopOwner_IdAndStatusIn(
                        shopId,
                        productStatuses,
                        pageRequest
                );
            } else if (isAdmin) {
                birds = birdRepository.findAllByStatusIn(
                        productStatuses,
                        pageRequest
                );
            }

            if (birds.isPresent()) {
                return getPageNumberWrapperBirds(birds);
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Do not have product have this status.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else if (productFilter.getCategory() == 2) {
            Optional<Page<Food>> foods = Optional.empty();
            if (isShopOwner) {
                foods = foodRepository.findAllByShopOwner_IdAndStatusIn(
                        shopId,
                        productStatuses,
                        pageRequest
                );
            } else if (isAdmin) {
                foods = foodRepository.findAllByStatusIn(
                        productStatuses,
                        pageRequest
                );
            }

            if (foods.isPresent()) {
                return getPageNumberWrapperFoods(foods);
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Do not have product have this status.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else if (productFilter.getCategory() == 3) {
            Optional<Page<Accessory>> accessories = Optional.empty();
            if (isShopOwner) {
                accessories = accessoryRepository.findAllByShopOwner_IdAndStatusIn(
                        shopId,
                        productStatuses,
                        pageRequest
                );
            } else if (isAdmin) {
                accessories = accessoryRepository.findAllByStatusIn(
                        productStatuses,
                        pageRequest
                );
            }

            if (accessories.isPresent()) {
                return getPageNumberWrapperAccessories(accessories);
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Do not have product have this status.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else {
            return getErrorResponseNotFoundCategory();
        }
    }

    private ResponseEntity<?> filterProductByDiscountedPriceGreaterThanOrEqual(ProductShopOwnerFilterDto productFilter, Long shopId, PageRequest pageRequest, boolean isShopOwner, boolean isAdmin) {
        if (productFilter.getCategory() == 1) {
            Optional<Page<Bird>> birds = Optional.empty();
            if (isShopOwner) {
                birds = birdRepository.findAllByShopOwner_IdAndProductSummary_DiscountedPriceGreaterThanEqualAndStatusIn(
                        shopId,
                        Double.parseDouble(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER,
                        pageRequest
                );
            } else if (isAdmin) {
                birds = birdRepository.findAllByProductSummary_DiscountedPriceGreaterThanEqualAndStatusIn(
                        Double.parseDouble(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (birds.isPresent()) {
                return getPageNumberWrapperBirds(birds);
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Do not have product greater than this price.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else if (productFilter.getCategory() == 2) {
            Optional<Page<Food>> foods = Optional.empty();
            if (isShopOwner) {
                foods = foodRepository.findAllByShopOwner_IdAndProductSummary_DiscountedPriceGreaterThanEqualAndStatusIn(
                        shopId,
                        Double.parseDouble(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER,
                        pageRequest
                );
            } else if (isAdmin) {
                foods = foodRepository.findAllByProductSummary_DiscountedPriceGreaterThanEqualAndStatusIn(
                        Double.parseDouble(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (foods.isPresent()) {
                return getPageNumberWrapperFoods(foods);
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Do not have product greater than this price.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else if (productFilter.getCategory() == 3) {
            Optional<Page<Accessory>> accessories = Optional.empty();
            if (isShopOwner) {
                accessories = accessoryRepository.findAllByShopOwner_IdAndProductSummary_DiscountedPriceGreaterThanEqualAndStatusIn(
                        shopId,
                        Double.parseDouble(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER,
                        pageRequest
                );
            } else if (isAdmin) {
                accessories = accessoryRepository.findAllByProductSummary_DiscountedPriceGreaterThanEqualAndStatusIn(
                        Double.parseDouble(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (accessories.isPresent()) {
                return getPageNumberWrapperAccessories(accessories);
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Do not have product greater than this price.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else {
            return getErrorResponseNotFoundCategory();
        }
    }

    private ResponseEntity<?> filterProductByPriceGreaterThanOrEqual(
            ProductShopOwnerFilterDto productFilter, Long shopId,
            PageRequest pageRequest, boolean isShopOwner, boolean isAdmin
    ) {
        if (productFilter.getCategory() == 1) {
            Optional<Page<Bird>> birds = Optional.empty();
            if (isShopOwner) {
                birds = birdRepository.findAllByShopOwner_IdAndPriceGreaterThanEqualAndStatusIn(
                        shopId,
                        Double.parseDouble(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER,
                        pageRequest
                );
            } else if (isAdmin) {
                birds = birdRepository.findAllByPriceGreaterThanEqualAndStatusIn(
                        Double.parseDouble(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (birds.isPresent()) {
                return getPageNumberWrapperBirds(birds);
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Do not have product greater than this price.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else if (productFilter.getCategory() == 2) {
            Optional<Page<Food>> foods = Optional.empty();
            if (isShopOwner) {
                foods = foodRepository.findAllByShopOwner_IdAndPriceGreaterThanEqualAndStatusIn(
                        shopId,
                        Double.parseDouble(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER,
                        pageRequest
                );
            } else if (isAdmin) {
                foods = foodRepository.findAllByPriceGreaterThanEqualAndStatusIn(
                        Double.parseDouble(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (foods.isPresent()) {
                return getPageNumberWrapperFoods(foods);
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Do not have product greater than this price.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else if (productFilter.getCategory() == 3) {
            Optional<Page<Accessory>> accessories = Optional.empty();
            if (isShopOwner) {
                accessories = accessoryRepository.findAllByShopOwner_IdAndPriceGreaterThanEqualAndStatusIn(
                        shopId,
                        Double.parseDouble(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER,
                        pageRequest
                );
            } else if (isAdmin) {
                accessories = accessoryRepository.findAllByPriceGreaterThanEqualAndStatusIn(
                        Double.parseDouble(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (accessories.isPresent()) {
                return getPageNumberWrapperAccessories(accessories);
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Do not have product greater than this price.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else {
            return getErrorResponseNotFoundCategory();
        }
    }

    private ResponseEntity<?> filterProductByTypeNameLike(
            ProductShopOwnerFilterDto productFilter, Long shopId,
            PageRequest pageRequest, boolean isShopOwner, boolean isAdmin
    ) {
        if (productFilter.getCategory() == 1) {
            List<TypeBird> typeBirdIds = typeBirdRepository.findAllByNameLike(
                    "%" + productFilter.getProductSearchInfo().getValue() + "%"
            );

            Optional<Page<Bird>> birds = Optional.empty();
            if (isShopOwner) {
                birds = birdRepository.findAllByShopOwner_IdAndTypeBird_IdInAndStatusIn(
                        shopId,
                        typeBirdIds.stream().map(TypeBird::getId).collect(Collectors.toList()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER,
                        pageRequest
                );
            } else if (isAdmin) {
                birds = birdRepository.findAllByTypeBird_IdInAndStatusIn(
                        typeBirdIds.stream().map(TypeBird::getId).collect(Collectors.toList()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (birds.isPresent()) {
                return getPageNumberWrapperBirds(birds);
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Not found this type name.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else if (productFilter.getCategory() == 2) {
            List<TypeFood> typeFoodIds = typeFoodRepository.findAllByNameLike(
                    "%" + productFilter.getProductSearchInfo().getValue() + "%"
            );

            Optional<Page<Food>> foods = Optional.empty();
            if (isShopOwner) {
                foods = foodRepository.findAllByShopOwner_IdAndTypeFood_IdInAndStatusIn(
                        shopId,
                        typeFoodIds.stream().map(TypeFood::getId).collect(Collectors.toList()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER,
                        pageRequest
                );
            } else if (isAdmin) {
                foods = foodRepository.findAllByTypeFood_IdInAndStatusIn(
                        typeFoodIds.stream().map(TypeFood::getId).collect(Collectors.toList()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (foods.isPresent()) {
                return getPageNumberWrapperFoods(foods);
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Not found this type name.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else if (productFilter.getCategory() == 3) {
            List<TypeAccessory> typeAccessoryIds = typeAccessoryRepository.findAllByNameLike(
                    "%" + productFilter.getProductSearchInfo().getValue() + "%"
            );

            Optional<Page<Accessory>> accessories = Optional.empty();
            if (isShopOwner) {
                accessories = accessoryRepository.findAllByShopOwner_IdAndTypeAccessory_IdInAndStatusIn(
                        shopId,
                        typeAccessoryIds.stream().map(TypeAccessory::getId).collect(Collectors.toList()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER,
                        pageRequest
                );
            } else if (isAdmin) {
                accessories = accessoryRepository.findAllByTypeAccessory_IdInAndStatusIn(
                        typeAccessoryIds.stream().map(TypeAccessory::getId).collect(Collectors.toList()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (accessories.isPresent()) {
                return getPageNumberWrapperAccessories(accessories);
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Not found this type name.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else {
            return getErrorResponseNotFoundCategory();
        }
    }

    private ResponseEntity<?> filterProductByNameLike(
            ProductShopOwnerFilterDto productFilter, Long shopId,
            PageRequest pageRequest, boolean isShopOwner, boolean isAdmin
    ) {
        if (productFilter.getCategory() == 1) {
            String nameLike = "%" + productFilter.getProductSearchInfo().getValue() + "%";

            Optional<Page<Bird>> birds = Optional.empty();
            if (isShopOwner) {
                birds = birdRepository.findAllByNameLikeAndShopOwner_IdAndStatusIn(
                        nameLike, shopId, ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER, pageRequest
                );
            } else if (isAdmin) {
                birds = birdRepository.findAllByNameLikeAndStatusIn(
                        nameLike, ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN, pageRequest
                );
            }

            if (birds.isPresent()) {
                return getPageNumberWrapperBirds(birds);
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Not found this name.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else if (productFilter.getCategory() == 2) {
            String nameLike = "%" + productFilter.getProductSearchInfo().getValue() + "%";

            Optional<Page<Food>> foods = Optional.empty();
            if (isShopOwner) {
                foods = foodRepository.findAllByNameLikeAndShopOwner_IdAndStatusIn(
                        nameLike, shopId, ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER, pageRequest
                );
            } else if (isAdmin) {
                foods = foodRepository.findAllByNameLikeAndStatusIn(
                        nameLike, ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN, pageRequest
                );
            }

            if (foods.isPresent()) {
                return getPageNumberWrapperFoods(foods);
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Not found this name.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else if (productFilter.getCategory() == 3) {
            String nameLike = "%" + productFilter.getProductSearchInfo().getValue() + "%";
            Optional<Page<Accessory>> accessories = Optional.empty();
            if (isShopOwner) {
                accessories = accessoryRepository.findAllByNameLikeAndShopOwner_IdAndStatusIn(
                        nameLike, shopId, ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER, pageRequest
                );
            } else if (isAdmin) {
                accessories = accessoryRepository.findAllByNameLikeAndStatusIn(
                        nameLike, ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN, pageRequest
                );
            }

            if (accessories.isPresent()) {
                return getPageNumberWrapperAccessories(accessories);
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Not found this name.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else {
            return getErrorResponseNotFoundCategory();
        }
    }

    private ResponseEntity<?> filterProductByIdEqual(
            ProductShopOwnerFilterDto productFilter, Long shopId,
            PageRequest pageRequest, boolean isShopOwner, boolean isAdmin
    ) {
        if (productFilter.getCategory() == 1) {
            Optional<Page<Bird>> birds = Optional.empty();
            if (isShopOwner) {
                birds = birdRepository.findByIdAndShopOwner_IdAndStatusIn(
                        Long.valueOf(productFilter.getProductSearchInfo().getValue()),
                        shopId,
                        ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER,
                        pageRequest
                );
            } else if (isAdmin) {
                birds = birdRepository.findByIdAndStatusIn(
                        Long.valueOf(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (birds.isPresent()) {
                return getPageNumberWrapperBirds(birds);
            }
        } else if (productFilter.getCategory() == 2) {
            Optional<Page<Food>> foods = Optional.empty();
            if (isShopOwner) {
                foods = foodRepository.findByIdAndShopOwner_IdAndStatusIn(
                        Long.valueOf(productFilter.getProductSearchInfo().getValue()),
                        shopId,
                        ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER,
                        pageRequest
                );
            } else if (isAdmin) {
                foods = foodRepository.findByIdAndStatusIn(
                        Long.valueOf(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (foods.isPresent()) {
                return getPageNumberWrapperFoods(foods);
            }
        } else if (productFilter.getCategory() == 3) {
            Optional<Page<Accessory>> accessories = Optional.empty();
            if (isShopOwner) {
                accessories = accessoryRepository.findByIdAndShopOwner_IdAndStatusIn(
                        Long.valueOf(productFilter.getProductSearchInfo().getValue()),
                        shopId,
                        ProductStatusConstant.LIST_STATUS_GET_FOR_SHOP_OWNER,
                        pageRequest
                );
            } else if (isAdmin) {
                accessories = accessoryRepository.findByIdAndStatusIn(
                        Long.valueOf(productFilter.getProductSearchInfo().getValue()),
                        ProductStatusConstant.LIST_STATUS_GET_FOR_ADMIN,
                        pageRequest
                );
            }

            if (accessories.isPresent()) {
                return getPageNumberWrapperAccessories(accessories);
            }
        } else {
            return getErrorResponseNotFoundCategory();
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found this id.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<ErrorResponse> getErrorResponseNotFoundCategory() {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found this category.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<PageNumberWrapper<ProductShopDto>> getPageNumberWrapperAccessories(Optional<Page<Accessory>> accessories) {
        List<ProductShopDto> listAccessoryShopDto = accessories.get().stream()
                .map(this::productToProductShopDto)
                .toList();
        PageNumberWrapper<ProductShopDto> result = new PageNumberWrapper<>(
                listAccessoryShopDto,
                accessories.get().getTotalPages(),
                accessories.get().getTotalElements()
        );
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<PageNumberWrapper<ProductShopDto>> getPageNumberWrapperFoods(Optional<Page<Food>> foods) {
        List<ProductShopDto> listFoodShopDto = foods.get().stream()
                .map(this::productToProductShopDto)
                .toList();
        PageNumberWrapper<ProductShopDto> result = new PageNumberWrapper<>(
                listFoodShopDto,
                foods.get().getTotalPages(),
                foods.get().getTotalElements()
        );
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<PageNumberWrapper<ProductShopDto>> getPageNumberWrapperBirds(Optional<Page<Bird>> birds) {
        List<ProductShopDto> listBirdShopDto = birds.get().stream()
                .map(this::productToProductShopDto)
                .toList();
        PageNumberWrapper<ProductShopDto> result = new PageNumberWrapper<>(
                listBirdShopDto,
                birds.get().getTotalPages(),
                birds.get().getTotalElements()
        );
        return ResponseEntity.ok(result);
    }

    @Override
    public ProductCartDto productToProductCart(Product product) {
        if (product != null) {
            ProductCartDto productCartDto = ProductCartDto.builder()
                    .id(product.getId())
                    .name(product.getName())
                    .price(product.getPrice())
                    .imgUrl(MyUtils.toLists(product.getImgUrl(), ",").get(0))
                    .discountRate(this.CalculateSaleOff(product.getPromotionShops(), product.getPrice()))
                    .quantity(product.getQuantity())
                    .star(this.CalculationRating(product.getOrderDetails()))
                    .build();
            productCartDto.setDiscountedPrice(this.CalculateDiscountedPrice(product.getPrice(),
                    productCartDto.getDiscountRate()));
            if (product instanceof Bird) {
                productCartDto.setCategoryId(Category.getCategoryIdByName(new BirdDto().getClass().getSimpleName()));
                productCartDto.setTags(tagMapper.listModelToListDto(((Bird) product).getTags()));
                productCartDto.setType(typeMapper.modelToDto(((Bird) product).getTypeBird()));
            } else if (product instanceof Food) {
                productCartDto.setCategoryId(Category.getCategoryIdByName(new FoodDto().getClass().getSimpleName()));
                productCartDto.setTags(tagMapper.listModelToListDto(((Food) product).getTags()));
                productCartDto.setType(typeMapper.modelToDto(((Food) product).getTypeFood()));
            } else if (product instanceof Accessory) {
                productCartDto.setCategoryId(Category.getCategoryIdByName(new AccessoryDto().getClass().getSimpleName()));
                productCartDto.setTags(tagMapper.listModelToListDto(((Accessory) product).getTags()));
                productCartDto.setType(typeMapper.modelToDto(((Accessory) product).getTypeAccessory()));
            }
            AddressDto address = addressMapper.toDto(product.getShopOwner().getAddress());
            ShopOwnerDto shopOwner = shopOwnerMapper.modelToDto(product.getShopOwner());
            productCartDto.setShopOwner(shopOwner);
            return productCartDto;
        }
        return null;
    }

    private PageNumberWrapper<Long> getAllIdBirdByFilter(ProductFilterDto filterDto) {
        filterDto = this.checkProductFilterDto(filterDto);
        PageRequest pageRequest = this.getSortDirect(filterDto);
        List<String> listName = MyUtils.splitStringToList(filterDto.getName(), " ");

        Page<Long> pageAble = birdRepository.idFilter(filterDto.getName(), filterDto.getListTypeId(),
                filterDto.getStar(), filterDto.getLowestPrice(), filterDto.getHighestPrice(),filterDto.getShopId()
                ,filterDto.getCheckListTypeId(), ShopOwnerConstant.STATUS_SHOP_PRODUCT_FOR_USER_STRING,pageRequest);
        PageNumberWrapper<Long> productDtoPageNumberWrapper = new PageNumberWrapper<>();
        productDtoPageNumberWrapper.setLists(pageAble.getContent());
        productDtoPageNumberWrapper.setPageNumber(pageAble.getTotalPages());
        return productDtoPageNumberWrapper;
    }

    private PageNumberWrapper<Long> getAllIdFoodFilter(ProductFilterDto filterDto) {
        filterDto = this.checkProductFilterDto(filterDto);
        PageRequest pageRequest = this.getSortDirect(filterDto);

        Page<Long> pageAble = foodRepository.idFilter(filterDto.getName(), filterDto.getListTypeId(),
                filterDto.getStar(), filterDto.getLowestPrice(), filterDto.getHighestPrice(), filterDto.getShopId()
                ,filterDto.getCheckListTypeId(), ShopOwnerConstant.STATUS_SHOP_PRODUCT_FOR_USER_STRING, pageRequest);
        PageNumberWrapper<Long> productDtoPageNumberWrapper = new PageNumberWrapper<>();
        productDtoPageNumberWrapper.setLists(pageAble.getContent());
        productDtoPageNumberWrapper.setPageNumber(pageAble.getTotalPages());
//        log.info("list id after filter {}",id );
        return productDtoPageNumberWrapper;
    }

    private PageNumberWrapper<Long> getAllIdAccessoryFilter(ProductFilterDto filterDto) {
        filterDto = this.checkProductFilterDto(filterDto);
        PageRequest pageRequest = this.getSortDirect(filterDto);

        Page<Long> pageAble = accessoryRepository.idFilter(filterDto.getName(), filterDto.getListTypeId(),
                filterDto.getStar(), filterDto.getLowestPrice(), filterDto.getHighestPrice(), filterDto.getShopId()
                ,filterDto.getCheckListTypeId(), ShopOwnerConstant.STATUS_SHOP_PRODUCT_FOR_USER_STRING, pageRequest);
        PageNumberWrapper<Long> productDtoPageNumberWrapper = new PageNumberWrapper<>();
        productDtoPageNumberWrapper.setLists(pageAble.getContent());
        productDtoPageNumberWrapper.setPageNumber(pageAble.getTotalPages());

        return productDtoPageNumberWrapper;
    }

    private ProductFilterDto checkProductFilterDto(ProductFilterDto filterDto) {
        if (filterDto.getListTypeId() == null || filterDto.getListTypeId().size() == 0){
            filterDto.setListTypeId(null);
            filterDto.setCheckListTypeId(null);
        }else{
            filterDto.setCheckListTypeId(1l);
        }

        if (filterDto.getName() == null || filterDto.getName().isEmpty())
            filterDto.setName("%");
        else
            filterDto.setName(filterDto.getName().trim());
        if (filterDto.getHighestPrice() == 0.0)
            filterDto.setHighestPrice(PagingAndSorting.HIGHEST_PRICE_FILTER);
        if (filterDto.getStar() == 1)
            filterDto.setStar(PagingAndSorting.DEFAULT_STAR_FILTER);
        if (filterDto.getLowestPrice() == 0.0)
            filterDto.setLowestPrice(PagingAndSorting.LOWEST_PRICE_FILTER);
        if(filterDto.getShopId() == null || filterDto.getShopId() == -1 )
            filterDto.setShopId(null);
        return filterDto;
    }

    private PageRequest getSortDirect(ProductFilterDto filterDto) {
        PageRequest pageRequest;
        String sortDirect = Optional.ofNullable(filterDto)
                .map(dto -> dto.getSortPrice())
                .map(sortPrice -> sortPrice.getSortDirect())
                .orElse("Increase");
        if (sortDirect.equals("Increase")) {
            pageRequest = PageRequest.of(filterDto.getPageNumber() - 1, PagingAndSorting.DEFAULT_PAGE_SIZE,
                    Sort.by(Sort.Direction.ASC, "discounted_price"));
        } else {
            pageRequest = PageRequest.of(filterDto.getPageNumber() - 1, PagingAndSorting.DEFAULT_PAGE_SIZE,
                    Sort.by(Sort.Direction.DESC, "discounted_price"));
        }
        return pageRequest;
    }

    @Override
    public ResponseEntity<?> filter(ProductFilterDto filterDto) {
        PageNumberWrapper<Long> productDtoPageNumberWrapper = new PageNumberWrapper<>();
        List<Long> filterProductIds = new ArrayList<>();
        if (filterDto.getCategory() == 1) {
            productDtoPageNumberWrapper = this.getAllIdBirdByFilter(filterDto);
        } else if (filterDto.getCategory() == 2) {
            productDtoPageNumberWrapper = this.getAllIdFoodFilter(filterDto);
        } else if (filterDto.getCategory() == 3) {
            productDtoPageNumberWrapper = this.getAllIdAccessoryFilter(filterDto);
        }
        List<Product> listTemp = productRepository.findAllById(productDtoPageNumberWrapper.getLists());
        List<ProductCartDto> listdtos = this.listModelToDto(listTemp);
        String sortDirect = Optional.ofNullable(filterDto)
                .map(dto -> dto.getSortPrice())
                .map(sortPrice -> sortPrice.getSortDirect())
                .orElse("Increase");
        if (sortDirect.equals("Increase")) {

            if (listdtos != null) {
                Collections.sort(listdtos, new Comparator<ProductCartDto>() {
                    @Override
                    public int compare(ProductCartDto o1, ProductCartDto o2) {
                        return (int) (o1.getDiscountedPrice() - o2.getDiscountedPrice());
                    }
                });
            }

        } else {
            if (listdtos != null) {
                Collections.sort(listdtos, new Comparator<ProductCartDto>() {
                    @Override
                    public int compare(ProductCartDto o1, ProductCartDto o2) {
                        return (int) (-o1.getDiscountedPrice() + o2.getDiscountedPrice());
                    }
                });
            }
        }

        PageNumberWrapper<ProductCartDto> result = new PageNumberWrapper<>();
        result.setLists(listdtos);
        result.setPageNumber(productDtoPageNumberWrapper.getPageNumber());
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<?> addNewProduct(
            List<MultipartFile> multipartImgList,
            MultipartFile multipartVideo,
            ProductShopOwnerDto productShopOwnerDto
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        Optional<Account> account = accountRepository.findByEmail(username);
        String originUrl = appProperties.getS3().getUrl();
        String urlVideo = "";
        List<String> urlImgList = new ArrayList<>();
        if (account.get().getRole() == UserRole.SHOPOWNER) {
            if (multipartImgList != null && !multipartImgList.isEmpty()) {
                for (MultipartFile multipartFile : multipartImgList) {
                    String newFilename = FileNameUtils.getNewImageFileName(multipartFile);
                    urlImgList.add(originUrl + newFilename);
                    try {
                        S3Utils.uploadFile(newFilename, multipartFile.getInputStream());
                    } catch (Exception ex) {
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                                .errorMessage("Upload file fail")
                                .build();
                        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
                    }
                }
            }

            String imgUrl = urlImgList.stream()
                    .collect(Collectors.joining(","));


            if (multipartVideo != null && !multipartVideo.isEmpty()) {
                String newFilename = FileNameUtils.getNewVideoFileName(multipartVideo);
                urlVideo = originUrl + newFilename;
                try {
                    S3Utils.uploadFile(newFilename, multipartVideo.getInputStream());
                } catch (Exception ex) {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                            .errorMessage("Upload file fail")
                            .build();
                    return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
                }
            }

            log.info("productShopOwnerDto.toString() {}", productShopOwnerDto.toString());
            if (productShopOwnerDto.getCategoryId() == 1) {
                Bird bird = new Bird();
                bird.setName(productShopOwnerDto.getName());
                bird.setPrice(productShopOwnerDto.getPrice());
                bird.setDescription(productShopOwnerDto.getDescription());
                bird.setQuantity(productShopOwnerDto.getQuantity());
                bird.setImgUrl(imgUrl);
                bird.setVideoUrl(urlVideo);
                bird.setTypeBird(typeBirdRepository.findById(productShopOwnerDto.getTypeId()).get());
                bird.setAge(productShopOwnerDto.getFeature().getAge());
                bird.setGender(productShopOwnerDto.getFeature().getGender());
                bird.setColor(productShopOwnerDto.getFeature().getColor());
                bird.setShopOwner(account.get().getShopOwner());
                bird.setStatus(ProductStatus.ACTIVE);
                Bird saveBird = productRepository.save(bird);
                productSummaryService.updateCategory(saveBird);
                if (productShopOwnerDto.getPromotionShopId() != null && !productShopOwnerDto.getPromotionShopId().isEmpty()) {
                    List<PromotionShop> promotionShops = promotionShopRepository.findAllPromotionShopByIdIn(productShopOwnerDto.getPromotionShopId());
                    bird.setPromotionShops(promotionShops);
//                    promotionShops.stream().forEach(promotionShop -> {
//                        promotionShop.addProduct(bird);
//                        promotionShopRepository.save(promotionShop);
//                    });
                }
                if (productShopOwnerDto.getTagId() != null && !productShopOwnerDto.getTagId().isEmpty()) {
                    List<Tag> tagPresentInList = tagRepository.findByIdIn(productShopOwnerDto.getTagId());
                    bird.setTags(tagPresentInList);
//                    tagPresentInList.stream().forEach(tag -> {
//                        tag.addBirds(bird);
//                        tagRepository.save(tag);
//                    });
                }
                productRepository.save(bird);
            } else if (productShopOwnerDto.getCategoryId() == 2) {
                Food food = new Food();
                food.setName(productShopOwnerDto.getName());
                food.setPrice(productShopOwnerDto.getPrice());
                food.setDescription(productShopOwnerDto.getDescription());
                food.setQuantity(productShopOwnerDto.getQuantity());
                food.setImgUrl(imgUrl);
                food.setVideoUrl(urlVideo);
                food.setTypeFood(typeFoodRepository.findById(productShopOwnerDto.getTypeId()).get());
                food.setWeight(productShopOwnerDto.getFeature().getWeight());
                food.setShopOwner(account.get().getShopOwner());
                food.setStatus(ProductStatus.ACTIVE);
                Food saveFood = productRepository.save(food);
                productSummaryService.updateCategory(saveFood);
                if (productShopOwnerDto.getPromotionShopId() != null && !productShopOwnerDto.getPromotionShopId().isEmpty()) {
                    List<PromotionShop> promotionShops = promotionShopRepository.findAllPromotionShopByIdIn(productShopOwnerDto.getPromotionShopId());
                    food.setPromotionShops(promotionShops);
//                    promotionShops.stream().forEach(promotionShop -> {
//                        promotionShop.addProduct(food);
//                        promotionShopRepository.save(promotionShop);
//                    });
                }
                if (productShopOwnerDto.getTagId() != null && !productShopOwnerDto.getTagId().isEmpty()) {
                    List<Tag> tagPresentInList = tagRepository.findByIdIn(productShopOwnerDto.getTagId());
                    food.setTags(tagPresentInList);
//                    tagPresentInList.stream().forEach(tag -> {
//                        tag.addFoods(food);
//                        tagRepository.save(tag);
//                    });
                }
                productRepository.save(food);
            } else if (productShopOwnerDto.getCategoryId() == 3) {
                Accessory accessory = new Accessory();
                accessory.setName(productShopOwnerDto.getName());
                accessory.setPrice(productShopOwnerDto.getPrice());
                accessory.setDescription(productShopOwnerDto.getDescription());
                accessory.setQuantity(productShopOwnerDto.getQuantity());
                accessory.setImgUrl(imgUrl);
                accessory.setVideoUrl(urlVideo);
                accessory.setTypeAccessory(typeAccessoryRepository.findById(productShopOwnerDto.getTypeId()).get());
                accessory.setOrigin(productShopOwnerDto.getFeature().getOrigin());
                accessory.setShopOwner(account.get().getShopOwner());
                accessory.setStatus(ProductStatus.ACTIVE);
                Accessory saveAccessory = productRepository.save(accessory);
                productSummaryService.updateCategory(saveAccessory);
                if (productShopOwnerDto.getPromotionShopId() != null && !productShopOwnerDto.getPromotionShopId().isEmpty()) {
                    List<PromotionShop> promotionShops = promotionShopRepository.findAllPromotionShopByIdIn(productShopOwnerDto.getPromotionShopId());
                    accessory.setPromotionShops(promotionShops);
//                    promotionShops.stream().forEach(promotionShop -> {
//                        promotionShop.addProduct(accessory);
//                        promotionShopRepository.save(promotionShop);
//                    });
                }
                if (productShopOwnerDto.getTagId() != null && !productShopOwnerDto.getTagId().isEmpty()) {
                    List<Tag> tagPresentInList = tagRepository.findByIdIn(productShopOwnerDto.getTagId());
                    accessory.setTags(tagPresentInList);
//                    tagPresentInList.stream().forEach(tag -> {
//                        tag.addAccessories(accessory);
//                        tagRepository.save(tag);
//                    });
                }
                productRepository.save(accessory);
            }
            SuccessResponse successResponse = SuccessResponse.builder()
                    .successMessage("Add new product successfully.")
                    .successCode(String.valueOf(HttpStatus.OK.value()))
                    .build();
            return new ResponseEntity<>(successResponse, HttpStatus.OK);
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                .errorMessage("Something went wrong!")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    private PageRequest getSortDirect(ShopFilterDto shopFilterDto) {
        String sortDirect = Optional.ofNullable(shopFilterDto)
                .map(dto -> dto.getSortPrice())
                .map(sortPrice -> sortPrice.getSortDirect())
                .orElse("Increase");
        PageRequest pageRequest;
        if (sortDirect.equals("Increase")) {
            pageRequest = PageRequest.of(shopFilterDto.getPageNumber() - 1, PagingAndSorting.DEFAULT_PAGE_SIZE,
                    Sort.by(Sort.Direction.ASC, "price"));
        } else {
            pageRequest = PageRequest.of(shopFilterDto.getPageNumber() - 1, PagingAndSorting.DEFAULT_PAGE_SIZE,
                    Sort.by(Sort.Direction.DESC, "price"));
        }
        return pageRequest;
    }

    private PageNumberWrapper setPageNumberWrapper(Page page) {
        if (page != null) {
            PageNumberWrapper<Long> productDtoPageNumberWrapper = new PageNumberWrapper<>();
            productDtoPageNumberWrapper.setLists(page.getContent());
            productDtoPageNumberWrapper.setPageNumber(page.getTotalPages());
            productDtoPageNumberWrapper.setTotalElement(page.getTotalElements());
            return productDtoPageNumberWrapper;
        }
        return null;
    }

}
