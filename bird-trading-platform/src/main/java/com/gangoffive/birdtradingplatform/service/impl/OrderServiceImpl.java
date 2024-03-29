package com.gangoffive.birdtradingplatform.service.impl;

import com.gangoffive.birdtradingplatform.api.response.ErrorResponse;
import com.gangoffive.birdtradingplatform.common.NotifiConstant;
import com.gangoffive.birdtradingplatform.common.OrderStatusConstant;
import com.gangoffive.birdtradingplatform.common.PagingAndSorting;
import com.gangoffive.birdtradingplatform.dto.*;
import com.gangoffive.birdtradingplatform.entity.*;
import com.gangoffive.birdtradingplatform.enums.*;
import com.gangoffive.birdtradingplatform.mapper.AddressMapper;
import com.gangoffive.birdtradingplatform.mapper.PromotionShopMapper;
import com.gangoffive.birdtradingplatform.repository.*;
import com.gangoffive.birdtradingplatform.service.NotificationService;
import com.gangoffive.birdtradingplatform.service.OrderDetailService;
import com.gangoffive.birdtradingplatform.service.OrderService;
import com.gangoffive.birdtradingplatform.util.DateUtils;
import com.gangoffive.birdtradingplatform.util.JsonUtil;
import com.gangoffive.birdtradingplatform.util.ResponseUtils;
import com.gangoffive.birdtradingplatform.wrapper.PageNumberWrapper;
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

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {
    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final PromotionShopMapper promotionShopMapper;
    private final PromotionShopRepository promotionShopRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final OrderDetailService orderDetailService;
    private final NotificationService notificationService;
    private final PackageOrderRepository packageOrderRepository;
    private final AddressMapper addressMapper;
    private final TransactionRepository transactionRepository;
    private final ShopStaffRepository shopStaffRepository;
    private final LogOrderRepository logOrderRepository;

    @Override
    public ResponseEntity<?> getAllOrderByPackageOrderId(Long packageOrderId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Optional<Account> account = accountRepository.findByEmail(authentication.getName());
        log.info("account.get().getId() {}", account.get().getId());
        Optional<List<Order>> orders = orderRepository.findAllByPackageOrder_IdAndPackageOrder_Account(packageOrderId, account.get());
//        log.info("orders {}", orders.get().size());
        if (orders.isPresent() && orders.get().size() > 0) {
            return ResponseEntity.ok(orders.get().stream().map(this::orderToOrderDto).collect(Collectors.toList()));
        } else {
            return ResponseUtils.getErrorResponseNotFound("No have orders with package order.");
        }
    }

    @Override
    public ResponseEntity<?> getAllOrderByShip(int pageNumber) {
        if (pageNumber > 0) {
            pageNumber--;
            PageRequest pageRequest = PageRequest.of(
                    pageNumber,
                    PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE,
                    Sort.by(Sort.Direction.DESC,
                            SortOrderColumn.LAST_UPDATE.getColumn()
                    )
            );
            Optional<Page<Order>> allOrdersForShip = orderRepository.findAllByStatusIn(OrderStatusConstant.VIEW_ORDER_STATUS_BY_SHIP, pageRequest);
            if (allOrdersForShip.isPresent()) {
                return getPageNumberWrapperWithOrders(allOrdersForShip, false, true);
            }
            return ResponseUtils.getErrorResponseNotFound("No have order.");
        }
        return ResponseUtils.getErrorResponseBadRequestPageNumber();
    }

    @Override
    public ResponseEntity<?> updateStatusOrderOfShipping(ChangeStatusListIdDto changeStatusListIdDto, String token) {
        boolean condition = true; // do something to check token
        if (condition) {
            if (changeStatusListIdDto.getStatus() >= OrderStatus.SHIPPED.getStatusCode()
                    || changeStatusListIdDto.getStatus() == OrderStatus.CANCELLED.getStatusCode()) {
                OrderStatus orderStatus = OrderStatus.getOrderStatusBaseOnStatusCode(changeStatusListIdDto.getStatus());
                int result = orderRepository.updateStatusOfListId(
                        orderStatus,
                        changeStatusListIdDto.getIds());
                if (result == changeStatusListIdDto.getIds().size()) {
                    List<Order> orders = orderRepository.findAllById(changeStatusListIdDto.getIds());
                    List<Long> packageOrderIds = orders.stream().map(order -> order.getPackageOrder().getId()).distinct().toList();
                    packageOrderIds.forEach(packageOrderId -> {
                        Optional<List<Order>> orderList = orderRepository.findAllByPackageOrder_Id(packageOrderId);
                        if (orderList.isPresent()) {
                            boolean checkDelivered = orderList.get().stream().allMatch(tmp -> tmp.getStatus().equals(OrderStatus.DELIVERED));
                            if (checkDelivered) {
                                Optional<Transaction> transaction = transactionRepository.findByPackageOrder_Id(packageOrderId);
                                if (transaction.isPresent()) {
                                    transaction.get().setStatus(TransactionStatus.SUCCESS);
                                    transactionRepository.save(transaction.get());
                                }
                            }
                        }
                    });

                    List<Long> userIdList = packageOrderRepository.findAllAccountIdByOrderIds(changeStatusListIdDto.getIds()).get();
                    NotificationDto noti = new NotificationDto();
                    noti.setName((NotifiConstant.ORDER_NAME_NOTI_USER));
                    noti.setNotiText(orderStatus.getDescription());
                    noti.setRole(NotifiConstant.NOTI_USER_ROLE);
                    notificationService.pushNotificationForListUserID(userIdList, noti);
                    if(orderStatus.name().equalsIgnoreCase(OrderStatus.DELIVERED.name())){
                        List<Long> accountIdOfShop = orderRepository.findAllAccountIdOfShopByListOrderId(changeStatusListIdDto.getIds()).get();
                        noti.setRole(NotifiConstant.NOTI_SHOP_ROLE);
                        noti.setNotiText(String.format(NotifiConstant.ORDER_SUCCESS_DELIVERED_TO_CUSTOMER,changeStatusListIdDto.getIds().toString()));
                        notificationService.pushNotificationForListUserID(accountIdOfShop, noti);
                    }
                        return ResponseEntity.ok("Update success");
                } else {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                            .errorMessage(String.format("Update fail %d order", changeStatusListIdDto.getIds().size() - result))
                            .build();
                    return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
                }
            } else {
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                        .errorMessage("Some thing went wrong!")
                        .build();
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
            }
        } else {
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                    .errorMessage("Token not valid!")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public ResponseEntity<?> getAllOrderDetailByOrderId(Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<Account> account = accountRepository.findByEmail(username);
        ShopOwner shopOwner;
        if (account.isPresent()) {
            shopOwner = account.get().getShopOwner();
        } else {
            Optional<ShopStaff> shopStaff = shopStaffRepository.findByUserName(username);
            if (shopStaff.isPresent()) {
                shopOwner = shopStaff.get().getShopOwner();
            } else {
                return ResponseUtils.getErrorResponseBadRequest("Not have account");
            }
        }

        Optional<Order> order = orderRepository.findByShopOwnerAndId(shopOwner, id);
        if (order.isPresent()) {
            return ResponseEntity.ok(orderToOrderDto(order.get()));
        } else {
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                    .errorMessage("Order id not correct.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public ResponseEntity<?> filterAllOrder(OrderShopOwnerFilterDto orderFilter, boolean isShopOwner, boolean isAdmin) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<Account> account = accountRepository.findByEmail(username);
        Long shopId;
        if (account.isPresent() && isShopOwner) {
            shopId = account.get().getShopOwner().getId();
        } else if (account.isEmpty() && isShopOwner) {
            Optional<ShopStaff> shopStaff = shopStaffRepository.findByUserName(username);
            if (shopStaff.isPresent()) {
                shopId = shopStaff.get().getShopOwner().getId();
            } else {
                return ResponseUtils.getErrorResponseBadRequest("Not have staff account");
            }
        } else if (isAdmin) {
            shopId = 0L;
        } else {
            return ResponseUtils.getErrorResponseBadRequest("Not have account");
        }
        if (orderFilter.getPageNumber() > 0) {
            int pageNumber = orderFilter.getPageNumber() - 1;
            PageRequest pageRequest = PageRequest.of(pageNumber, PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE);
            PageRequest pageRequestWithSort = null;

            if (orderFilter.getSortDirection() != null
                    && !orderFilter.getSortDirection().getSort().isEmpty()
                    && !orderFilter.getSortDirection().getField().isEmpty()
            ) {
                if (
                        !SortOrderColumn.checkField(orderFilter.getSortDirection().getField())
                ) {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                            .errorMessage("Not found this field in sort direction.")
                            .build();
                    return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
                }
                if (orderFilter.getSortDirection().getSort().toUpperCase().equals(Sort.Direction.ASC.name())) {
                    pageRequestWithSort = getPageRequest(orderFilter, pageNumber, Sort.Direction.ASC);
                } else if (orderFilter.getSortDirection().getSort().toUpperCase().equals(Sort.Direction.DESC.name())) {
                    pageRequestWithSort = getPageRequest(orderFilter, pageNumber, Sort.Direction.DESC);
                } else {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                            .errorMessage("Not found this direction.")
                            .build();
                    return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
                }
            }

            if (
                    orderFilter.getOrderSearchInfo().getField().isEmpty()
                            && orderFilter.getOrderSearchInfo().getValue().isEmpty()
                            && orderFilter.getOrderSearchInfo().getOperator().isEmpty()
                            && orderFilter.getSortDirection().getField().isEmpty()
                            && orderFilter.getSortDirection().getSort().isEmpty()
            ) {
                log.info("all no");

                pageRequestWithSort = PageRequest.of(
                        pageNumber,
                        PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE,
                        Sort.by(Sort.Direction.DESC,
                                SortOrderColumn.LAST_UPDATE.getColumn())
                        );
                return filterAllOrderAllFieldEmpty(shopId, pageRequestWithSort, isShopOwner, isAdmin);
            } else if (
                    orderFilter.getOrderSearchInfo().getField().isEmpty()
                            && orderFilter.getOrderSearchInfo().getValue().isEmpty()
                            && orderFilter.getOrderSearchInfo().getOperator().isEmpty()
                            && !orderFilter.getSortDirection().getField().isEmpty()
                            && !orderFilter.getSortDirection().getSort().isEmpty()
            ) {
                log.info("with sort");
                return filterAllOrderAllFieldEmpty(shopId, pageRequestWithSort, isShopOwner, isAdmin);
            }

            if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.ID.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterOrderByIdEqual(orderFilter, shopId, pageRequest, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.PACKAGE_ORDER_ID.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
                            && orderFilter.getSortDirection().getField().isEmpty()
                            && orderFilter.getSortDirection().getSort().isEmpty()
                            && isAdmin
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterOrderByPackageOrderIdIdEqual(orderFilter, pageRequest);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.PACKAGE_ORDER_ID.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
                            && isAdmin
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterOrderByPackageOrderIdIdEqual(orderFilter, pageRequestWithSort);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.SHOP_ID.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
                            && orderFilter.getSortDirection().getField().isEmpty()
                            && orderFilter.getSortDirection().getSort().isEmpty()
                            && isAdmin
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterOrderByShopIdEqual(orderFilter, pageRequest);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.SHOP_ID.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
                            && isAdmin
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterOrderByShopIdEqual(orderFilter, pageRequestWithSort);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.ORDER_STATUS.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
                            && orderFilter.getSortDirection().getField().isEmpty()
                            && orderFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterOrderByStatusEqual(orderFilter, shopId, pageRequest, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.ORDER_STATUS.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterOrderByStatusEqual(orderFilter, shopId, pageRequestWithSort, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.PAYMENT_METHOD.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
                            && orderFilter.getSortDirection().getField().isEmpty()
                            && orderFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterOrderByPaymentMethodEqual(orderFilter, shopId, pageRequest, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.PAYMENT_METHOD.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterOrderByPaymentMethodEqual(orderFilter, shopId, pageRequestWithSort, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.PROMOTION_SHOP.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
                            && orderFilter.getSortDirection().getField().isEmpty()
                            && orderFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.CONTAIN.getOperator())) {
                    return filterOrderByPromotionIdContain(orderFilter, shopId, pageRequest, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.PROMOTION_SHOP.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.CONTAIN.getOperator())) {
                    return filterOrderByPromotionIdContain(orderFilter, shopId, pageRequestWithSort, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.TOTAL_PRICE.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
                            && orderFilter.getSortDirection().getField().isEmpty()
                            && orderFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.GREATER_THAN_OR_EQUAL.getOperator())) {
                    return filterOrderByTotalPriceGreaterThanOrEqual(orderFilter, shopId, pageRequest, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.TOTAL_PRICE.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.GREATER_THAN_OR_EQUAL.getOperator())) {
                    return filterOrderByTotalPriceGreaterThanOrEqual(orderFilter, shopId, pageRequestWithSort, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.SHIPPING_FEE.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
                            && orderFilter.getSortDirection().getField().isEmpty()
                            && orderFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.GREATER_THAN_OR_EQUAL.getOperator())) {
                    return filterOrderByShippingFeeGreaterThanOrEqual(orderFilter, shopId, pageRequest, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.SHIPPING_FEE.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.GREATER_THAN_OR_EQUAL.getOperator())) {
                    return filterOrderByShippingFeeGreaterThanOrEqual(orderFilter, shopId, pageRequestWithSort, isShopOwner, isAdmin);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.CREATED_DATE.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
                            && orderFilter.getSortDirection().getField().isEmpty()
                            && orderFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.FROM_TO.getOperator())) {
                    DateRangeDto dateRange = JsonUtil.INSTANCE.getObject(orderFilter.getOrderSearchInfo().getValue(), DateRangeDto.class);
                    if (dateRange.getDateTo() == -1L) {
                        return filterOrderByCreatedDateGreaterThanOrEqual(shopId, dateRange, pageRequest, isShopOwner, isAdmin);
                    } else {
                        return filterOrderByCreatedDateFromTo(shopId, dateRange, pageRequest, isShopOwner, isAdmin);
                    }
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.CREATED_DATE.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.FROM_TO.getOperator())) {
                    DateRangeDto dateRange = JsonUtil.INSTANCE.getObject(orderFilter.getOrderSearchInfo().getValue(), DateRangeDto.class);
                    if (dateRange.getDateTo() == -1L) {
                        return filterOrderByCreatedDateGreaterThanOrEqual(shopId, dateRange, pageRequestWithSort, isShopOwner, isAdmin);
                    } else {
                        return filterOrderByCreatedDateFromTo(shopId, dateRange, pageRequestWithSort, isShopOwner, isAdmin);
                    }
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.LASTED_UPDATE.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
                            && orderFilter.getSortDirection().getField().isEmpty()
                            && orderFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.FROM_TO.getOperator())) {
                    DateRangeDto dateRange = JsonUtil.INSTANCE.getObject(orderFilter.getOrderSearchInfo().getValue(), DateRangeDto.class);
                    if (dateRange.getDateTo() == -1L) {
                        return filterOrderByLastedDateGreaterThanOrEqual(shopId, dateRange, pageRequest, isShopOwner, isAdmin);
                    } else {
                        log.info("Han iu");
                        return filterOrderByLastedDateFromTo(shopId, dateRange, pageRequest, isShopOwner, isAdmin);
                    }
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    orderFilter.getOrderSearchInfo().getField().equals(FieldOrderTable.LASTED_UPDATE.getField())
                            && !orderFilter.getOrderSearchInfo().getValue().isEmpty()
            ) {
                if (orderFilter.getOrderSearchInfo().getOperator().equals(Operator.FROM_TO.getOperator())) {
                    DateRangeDto dateRange = JsonUtil.INSTANCE.getObject(orderFilter.getOrderSearchInfo().getValue(), DateRangeDto.class);
                    if (dateRange.getDateTo() == -1L) {
                        return filterOrderByLastedDateGreaterThanOrEqual(shopId, dateRange, pageRequestWithSort, isShopOwner, isAdmin);
                    } else {
                        return filterOrderByLastedDateFromTo(shopId, dateRange, pageRequestWithSort, isShopOwner, isAdmin);
                    }
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else {
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                        .errorMessage("Order filter is not correct.")
                        .build();
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
            }
        } else {
            ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.toString(),
                    "Page number cannot less than 1");
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }
    }

    private ResponseEntity<?> filterOrderByPackageOrderIdIdEqual(
            OrderShopOwnerFilterDto orderFilter, PageRequest pageRequest) {
        Optional<Page<Order>> orders = orderRepository.findAllByPackageOrder_Id(
                Long.valueOf(orderFilter.getOrderSearchInfo().getValue()),
                pageRequest
        );

        if (orders.isPresent()) {
            return getPageNumberWrapperWithOrders(orders, true, false);
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found order with this package order id.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterOrderByShopIdEqual(OrderShopOwnerFilterDto orderFilter, PageRequest pageRequest) {
        Optional<Page<Order>> orders = orderRepository.findAllByShopOwner_Id(
                Long.valueOf(orderFilter.getOrderSearchInfo().getValue()),
                pageRequest
        );

        if (orders.isPresent()) {
            return getPageNumberWrapperWithOrders(orders, true, false);
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found order in this shop.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterOrderByLastedDateFromTo(
            Long shopId,
            DateRangeDto dateRange,
            PageRequest pageRequest,
            boolean isShopOwner, boolean isAdmin) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(DateUtils.timeInMillisecondToDate(dateRange.getDateTo()));
        calendar.add(Calendar.DAY_OF_MONTH, 1);

        Optional<Page<Order>> orders = Optional.empty();
        if (isShopOwner) {
            orders = orderRepository.findAllByShopOwner_IdAndLastedUpdateBetweenAndStatusIn(
                    shopId,
                    DateUtils.timeInMillisecondToDate(dateRange.getDateFrom()),
                    calendar.getTime(),
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        } else if (isAdmin) {
            orders = orderRepository.findAllByLastedUpdateBetweenAndStatusIn(
                    DateUtils.timeInMillisecondToDate(dateRange.getDateFrom()),
                    calendar.getTime(),
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        }

        if (orders.isPresent()) {
            return getPageNumberWrapperWithOrders(orders, true, false);
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found order have lasted date between this range.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterOrderByLastedDateGreaterThanOrEqual(
            Long shopId,
            DateRangeDto dateRange,
            PageRequest pageRequest,
            boolean isShopOwner, boolean isAdmin) {
        Optional<Page<Order>> orders = Optional.empty();
        if (isShopOwner) {
            orders = orderRepository.findAllByShopOwner_IdAndLastedUpdateGreaterThanEqualAndStatusIn(
                    shopId,
                    DateUtils.timeInMillisecondToDate(dateRange.getDateFrom()),
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        } else if (isAdmin) {
            orders = orderRepository.findAllByLastedUpdateGreaterThanEqualAndStatusIn(
                    DateUtils.timeInMillisecondToDate(dateRange.getDateFrom()),
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        }

        if (orders.isPresent()) {
            return getPageNumberWrapperWithOrders(orders, true, false);
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found order have lasted date greater than or equal.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterOrderByCreatedDateFromTo(
            Long shopId,
            DateRangeDto dateRange,
            PageRequest pageRequest,
            boolean isShopOwner, boolean isAdmin) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(DateUtils.timeInMillisecondToDate(dateRange.getDateTo()));
        calendar.add(Calendar.DAY_OF_MONTH, 1);

        Optional<Page<Order>> orders = Optional.empty();
        if (isShopOwner) {
            orders = orderRepository.findAllByShopOwner_IdAndCreatedDateBetweenAndStatusIn(
                    shopId,
                    DateUtils.timeInMillisecondToDate(dateRange.getDateFrom()),
                    calendar.getTime(),
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        } else if (isAdmin) {
            orders = orderRepository.findAllByCreatedDateBetweenAndStatusIn(
                    DateUtils.timeInMillisecondToDate(dateRange.getDateFrom()),
                    calendar.getTime(),
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        }

        if (orders.isPresent()) {
            return getPageNumberWrapperWithOrders(orders, true, false);
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found order have created date between this range.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterOrderByCreatedDateGreaterThanOrEqual(
            Long shopId,
            DateRangeDto dateRange,
            PageRequest pageRequest,
            boolean isShopOwner, boolean isAdmin) {
        Optional<Page<Order>> orders = Optional.empty();
        if (isShopOwner) {
            orders = orderRepository.findAllByShopOwner_IdAndCreatedDateGreaterThanEqualAndStatusIn(
                    shopId,
                    DateUtils.timeInMillisecondToDate(dateRange.getDateFrom()),
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        } else if (isAdmin) {
            orders = orderRepository.findAllByCreatedDateGreaterThanEqualAndStatusIn(
                    DateUtils.timeInMillisecondToDate(dateRange.getDateFrom()),
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        }

        if (orders.isPresent()) {
            return getPageNumberWrapperWithOrders(orders, true, false);
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found order have created date greater than or equal.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterOrderByShippingFeeGreaterThanOrEqual(
            OrderShopOwnerFilterDto orderFilter,
            Long shopId,
            PageRequest pageRequest,
            boolean isShopOwner, boolean isAdmin) {
        Optional<Page<Order>> orders = Optional.empty();
        if (isShopOwner) {
            orders = orderRepository.findAllByShopOwner_IdAndShippingFeeGreaterThanEqualAndStatusIn(
                    shopId,
                    Double.parseDouble(orderFilter.getOrderSearchInfo().getValue()),
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        } else if (isAdmin) {
            orders = orderRepository.findAllByShippingFeeGreaterThanEqualAndStatusIn(
                    Double.parseDouble(orderFilter.getOrderSearchInfo().getValue()),
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        }

        if (orders.isPresent()) {
            return getPageNumberWrapperWithOrders(orders, true, false);
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found order have shipping fee greater than or equal.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterOrderByTotalPriceGreaterThanOrEqual(
            OrderShopOwnerFilterDto orderFilter,
            Long shopId, PageRequest pageRequest,
            boolean isShopOwner, boolean isAdmin) {
        Optional<Page<Order>> orders = Optional.empty();
        if (isShopOwner) {
            orders = orderRepository.findAllByShopOwner_IdAndTotalPriceGreaterThanEqualAndStatusIn(
                    shopId,
                    Double.parseDouble(orderFilter.getOrderSearchInfo().getValue()),
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        } else if (isAdmin) {
            orders = orderRepository.findAllByTotalPriceGreaterThanEqualAndStatusIn(
                    Double.parseDouble(orderFilter.getOrderSearchInfo().getValue()),
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        }

        if (orders.isPresent()) {
            return getPageNumberWrapperWithOrders(orders, true, false);
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found order have total price greater than or equal.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterOrderByPromotionIdContain(
            OrderShopOwnerFilterDto orderFilter,
            Long shopId,
            PageRequest pageRequest,
            boolean isShopOwner, boolean isAdmin) {
        Optional<PromotionShop> promotionShop = promotionShopRepository.findById(
                Long.valueOf(orderFilter.getOrderSearchInfo().getValue())
        );
        if (promotionShop.isPresent()) {
            Optional<List<OrderDetail>> orderDetailsContainingPromotion = Optional.empty();
            if (isShopOwner) {
                orderDetailsContainingPromotion =
                        orderDetailRepository.findAllByPromotionShopsContainingAndOrder_ShopOwner_IdAndOrder_StatusIn(
                                promotionShop.get(), shopId, OrderStatusConstant.VIEW_ALL_ORDER_STATUS
                        );
            } else if (isAdmin) {
                orderDetailsContainingPromotion =
                        orderDetailRepository.findAllByPromotionShopsContainingAndOrder_StatusIn(
                                promotionShop.get(), OrderStatusConstant.VIEW_ALL_ORDER_STATUS
                        );
            }

            if (orderDetailsContainingPromotion.isPresent()) {
                List<Long> orderIds = orderDetailsContainingPromotion.get().stream()
                        .map(orderDetail -> orderDetail.getOrder().getId())
                        .distinct()
                        .toList();
                Optional<Page<Order>> orders = orderRepository.findByIdIn(
                        orderIds,
                        pageRequest
                );
                if (orders.isPresent()) {
                    return getPageNumberWrapperWithOrders(orders, true, false);
                }
            }
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found this promotion id.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterOrderByPaymentMethodEqual(
            OrderShopOwnerFilterDto orderFilter,
            Long shopId,
            PageRequest pageRequest,
            boolean isShopOwner, boolean isAdmin) {
        List<PaymentMethod> paymentMethods;
        if (orderFilter.getOrderSearchInfo().getValue().trim().equals("9")) {
            paymentMethods = List.of(PaymentMethod.values());
        } else {
            paymentMethods = List.of(PaymentMethod.valueOf(orderFilter.getOrderSearchInfo().getValue()));
        }

        Optional<Page<Order>> orders = Optional.empty();
        if (isShopOwner) {
            orders = orderRepository.findAllByShopOwner_IdAndPackageOrder_PaymentMethodInAndStatusIn(
                    shopId,
                    paymentMethods,
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        } else if (isAdmin) {
            orders = orderRepository.findAllByPackageOrder_PaymentMethodInAndStatusIn(
                    paymentMethods,
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        }

        if (orders.isPresent()) {
            return getPageNumberWrapperWithOrders(orders, true, false);
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Do not have order have this payment method.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterOrderByStatusEqual(
            OrderShopOwnerFilterDto orderFilter,
            Long shopId,
            PageRequest pageRequest,
            boolean isShopOwner, boolean isAdmin) {
        List<OrderStatus> orderStatuses;
        if (Integer.parseInt(orderFilter.getOrderSearchInfo().getValue()) == 9) {
            orderStatuses = OrderStatusConstant.VIEW_ALL_ORDER_STATUS;
        } else {
            orderStatuses = Arrays.asList(
                    OrderStatus.getOrderStatusBaseOnStatusCode(
                            Integer.parseInt(orderFilter.getOrderSearchInfo().getValue())
                    )
            );
        }

        Optional<Page<Order>> orders = Optional.empty();
        if (isShopOwner) {
            orders = orderRepository.findAllByShopOwner_IdAndStatusIn(
                    shopId,
                    orderStatuses,
                    pageRequest
            );
        } else if (isAdmin) {
            orders = orderRepository.findAllByStatusIn(
                    orderStatuses,
                    pageRequest
            );
        }

        if (orders.isPresent()) {
            return getPageNumberWrapperWithOrders(orders, true, false);
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Do not have order have this status.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterOrderByIdEqual(
            OrderShopOwnerFilterDto orderFilter,
            Long shopId,
            PageRequest pageRequest,
            boolean isShopOwner, boolean isAdmin) {
        Optional<Page<Order>> orders = Optional.empty();
        if (isShopOwner) {
            orders = orderRepository.findByIdAndShopOwner_IdAndStatusIn(
                    Long.valueOf(orderFilter.getOrderSearchInfo().getValue()),
                    shopId,
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        } else if (isAdmin) {
            orders = orderRepository.findByIdAndStatusIn(
                    Long.valueOf(orderFilter.getOrderSearchInfo().getValue()),
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        }

        if (orders.isPresent()) {
            return getPageNumberWrapperWithOrders(orders, true, false);
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found this order id.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterAllOrderAllFieldEmpty(
            Long shopId,
            PageRequest pageRequest,
            boolean isShopOwner, boolean isAdmin) {
        Optional<Page<Order>> orders = Optional.empty();
        if (isShopOwner) {
            orders = orderRepository.findAllByShopOwner_IdAndStatusIn(
                    shopId,
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        } else if (isAdmin) {
            orders = orderRepository.findAllByStatusIn(
                    OrderStatusConstant.VIEW_ALL_ORDER_STATUS,
                    pageRequest
            );
        }

        if (orders.isPresent()) {
            return getPageNumberWrapperWithOrders(orders, true, false);
        } else {
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Not found order in shop.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        }
    }

    private ResponseEntity<PageNumberWrapper<?>> getPageNumberWrapperWithOrders(
            Optional<Page<Order>> orders, boolean isOrderShopOwner, boolean isOrderShip
    ) {
        if (isOrderShopOwner) {
            List<OrderShopOwnerDto> ordersShopOwnerDto = orders.get().stream()
                    .map(this::orderToOrderShopOwnerDto)
                    .collect(Collectors.toList());
            PageNumberWrapper<OrderShopOwnerDto> result = new PageNumberWrapper<>(
                    ordersShopOwnerDto,
                    orders.get().getTotalPages(),
                    orders.get().getTotalElements()
            );
            return ResponseEntity.ok(result);
        } else if (isOrderShip) {
            List<OrderShipDto> ordersShipDto = orders.get().stream()
                    .map(this::orderToOrderShipDto)
                    .collect(Collectors.toList());
            PageNumberWrapper<OrderShipDto> result = new PageNumberWrapper<>(
                    ordersShipDto,
                    orders.get().getTotalPages(),
                    orders.get().getTotalElements()
            );
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ofNullable(null);
    }

    private PageRequest getPageRequest(OrderShopOwnerFilterDto orderFilter, int pageNumber, Sort.Direction sortDirection) {
        return PageRequest.of(
                pageNumber,
                PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE,
                Sort.by(sortDirection,
                        SortOrderColumn.getColumnByField(orderFilter.getSortDirection().getField())
                )
        );
    }

    @Override
    public ResponseEntity<?> updateStatusOfListOrder(ChangeStatusListIdDto changeStatusListIdDto) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<Account> account = accountRepository.findByEmail(username);
        ShopStaff staff = new ShopStaff();
        Long shopId;
        boolean isShopStaff = false;
        if (account.isPresent()) {
            shopId = account.get().getShopOwner().getId();
        } else {
            Optional<ShopStaff> shopStaff = shopStaffRepository.findByUserName(username);
            if (shopStaff.isPresent()) {
                shopId = shopStaff.get().getShopOwner().getId();
                isShopStaff = true;
                staff = shopStaff.get();
            } else {
                return ResponseUtils.getErrorResponseBadRequest("Not have account");
            }
        }

        if (changeStatusListIdDto != null) {
            OrderStatus status = OrderStatus.getOrderStatusBaseOnStatusCode(changeStatusListIdDto.getStatus());
            if (OrderStatusConstant.UPDATE_ORDER_STATUS_SHOP_OWNER.contains(status)) {
                if (
                        orderRepository.checkIfOrderIdsBelongToShopId(
                                changeStatusListIdDto.getIds(),
                                shopId,
                                changeStatusListIdDto.getIds().size()
                        )
                ) {
                    int result = orderRepository.updateStatusOfListId(status, changeStatusListIdDto.getIds());
                    if (result == changeStatusListIdDto.getIds().size()) {
                        if (isShopStaff) {
                            List<Order> orders = orderRepository.findAllById(changeStatusListIdDto.getIds());
                            for (Order order : orders) {
                                LogOrder logOrder = new LogOrder();
                                logOrder.setStatus(OrderStatus.getOrderStatusBaseOnStatusCode(changeStatusListIdDto.getStatus()));
                                logOrder.setShopStaff(staff);
                                logOrder.setOrder(order);
                                logOrderRepository.save(logOrder);
                            }
                        }
                        if (changeStatusListIdDto.getStatus() == OrderStatus.SHIPPED.getStatusCode()) {
                            List<Long> userId = packageOrderRepository.findAllAccountIdByOrderIds(changeStatusListIdDto.getIds()).get();
                            NotificationDto noti = new NotificationDto();
                            noti.setName((NotifiConstant.ORDER_NAME_NOTI_USER));
                            noti.setNotiText(status.getDescription());
                            noti.setRole(NotifiConstant.NOTI_USER_ROLE);
                            notificationService.pushNotificationForListUserID(userId, noti);
                        }
                        return ResponseEntity.ok("Update success");
                    } else {
                        int numberUpdateFail = changeStatusListIdDto.getIds().size() - result;
                        return new ResponseEntity<>(ErrorResponse.builder().errorMessage(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                                .errorMessage(String.format("Update fail %d orders", numberUpdateFail)).build(), HttpStatus.BAD_REQUEST);
                    }
                } else {
                    return ResponseUtils.getErrorResponseBadRequest("Have order not of your shop.");
                }
            } else {
                return ResponseUtils.getErrorResponseBadRequest("This status can not change for shop owner");
            }
        } else {
            return ResponseUtils.getErrorResponseBadRequest("List change status is null.");
        }
    }

    private OrderDto orderToOrderDto(Order order) {
        ShopOwner shopOwner = order.getShopOwner();
        AddressDto addressDto = AddressDto.builder()
                .id(shopOwner.getAddress().getId())
                .address(shopOwner.getAddress().getAddress())
                .build();
        ShopOwnerDto shopOwnerDto = ShopOwnerDto.builder()
                .id(shopOwner.getId())
                .shopName(shopOwner.getShopName())
                .address(addressDto)
                .imgUrl(shopOwner.getAvatarImgUrl())
                .build();
        List<OrderDetailDto> orderDetailsDto = order.getOrderDetails().stream()
                .map(orderDetailService::orderDetailToOrderDetailDto)
                .toList();
        return OrderDto.builder()
                .orderId(order.getId())
                .orderStatus(order.getStatus())
                .shopOwner(shopOwnerDto)
                .totalPriceProduct(order.getTotalPrice())
                .orderDetails(orderDetailsDto)
                .shippingFee(order.getShippingFee())
                .address(addressMapper.toAddressInfoDto(order.getPackageOrder().getShippingAddress()))
                .paymentMethod(order.getPackageOrder().getPaymentMethod())
                .createdDate(order.getCreatedDate().getTime())
                .lastedUpdate(order.getLastedUpdate().getTime())
                .build();
    }

    private OrderShopOwnerDto orderToOrderShopOwnerDto(Order order) {
        OrderStatusDto orderStatus = OrderStatusDto.builder()
                .id(order.getStatus().getStatusCode())
                .status(order.getStatus())
                .build();
        List<OrderDetail> orderDetails = order.getOrderDetails();
        List<PromotionShop> promotionShops = new ArrayList<>();
        orderDetails.forEach(orderDetail -> promotionShops.addAll(getListPromotionShopByOrderDetail(orderDetail)));
        List<PromotionShopDto> promotionsShop = promotionShops.stream()
                .map(promotionShopMapper::modelToDto)
                .toList();
        Map<PromotionShopDto, Integer> promotionQuantityMap = new HashMap<>();
        for (PromotionShopDto promotionShop : promotionsShop) {
            promotionQuantityMap.merge(promotionShop, 1, Integer::sum);
        }
        List<PromotionShopOrderDto> promotionsShopOrder = new ArrayList<>();
        promotionQuantityMap.entrySet().forEach(entry -> {
            promotionsShopOrder.add(promotionShopDtoToPromotionShopOrderDto(entry.getKey(), entry.getValue()));
        });
        return OrderShopOwnerDto.builder()
                .id(order.getId())
                .packageOrderId(order.getPackageOrder().getId())
                .shopId(order.getShopOwner().getId())
                .totalPrice(order.getTotalPrice())
                .orderStatus(orderStatus)
                .shippingFee(order.getShippingFee())
                .paymentMethod(order.getPackageOrder().getPaymentMethod())
                .promotionsShop(promotionsShopOrder)
                .createdDate(order.getCreatedDate().getTime())
                .lastedUpdate(order.getLastedUpdate().getTime())
                .build();
    }

    private List<PromotionShop> getListPromotionShopByOrderDetail(OrderDetail orderDetail) {
        Optional<List<PromotionShop>> promotionShopList = promotionShopRepository.findAllByOrderDetail(orderDetail.getId());
        return promotionShopList.orElse(null);
    }

    private OrderShipDto orderToOrderShipDto(Order order) {
        OrderStatusDto orderStatus = OrderStatusDto.builder()
                .id(order.getStatus().getStatusCode())
                .status(order.getStatus())
                .build();
        return OrderShipDto.builder()
                .id(order.getId())
                .fullName(order.getPackageOrder().getShippingAddress().getFullName())
                .phoneNumber(order.getPackageOrder().getShippingAddress().getPhone())
                .address(order.getPackageOrder().getShippingAddress().getAddress())
                .totalPrice(order.getTotalPrice())
                .orderStatus(orderStatus)
                .shippingFee(order.getShippingFee())
                .paymentMethod(order.getPackageOrder().getPaymentMethod())
                .createdDate(order.getCreatedDate().getTime())
                .lastedUpdate(order.getLastedUpdate().getTime())
                .build();
    }

    private PromotionShopOrderDto promotionShopDtoToPromotionShopOrderDto(PromotionShopDto promotionShop, int quantity) {
        return PromotionShopOrderDto.builder()
                .id(promotionShop.getId())
                .name(promotionShop.getName())
                .description(promotionShop.getDescription())
                .discountRate(promotionShop.getDiscountRate())
                .quantity(quantity)
                .startDate(promotionShop.getStartDate())
                .endDate(promotionShop.getEndDate())
                .build();
    }
}
