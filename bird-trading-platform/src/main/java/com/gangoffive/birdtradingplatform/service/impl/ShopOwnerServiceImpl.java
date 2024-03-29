package com.gangoffive.birdtradingplatform.service.impl;


import com.gangoffive.birdtradingplatform.api.response.ErrorResponse;
import com.gangoffive.birdtradingplatform.api.response.SuccessResponse;
import com.gangoffive.birdtradingplatform.common.NotifiConstant;
import com.gangoffive.birdtradingplatform.common.PagingAndSorting;
import com.gangoffive.birdtradingplatform.config.AppProperties;
import com.gangoffive.birdtradingplatform.dto.*;
import com.gangoffive.birdtradingplatform.entity.*;
import com.gangoffive.birdtradingplatform.enums.*;
import com.gangoffive.birdtradingplatform.exception.CustomRuntimeException;
import com.gangoffive.birdtradingplatform.mapper.ShopOwnerMapper;
import com.gangoffive.birdtradingplatform.mapper.ShopStaffMapper;
import com.gangoffive.birdtradingplatform.repository.*;
import com.gangoffive.birdtradingplatform.security.UserPrincipal;
import com.gangoffive.birdtradingplatform.service.ChannelService;
import com.gangoffive.birdtradingplatform.service.JwtService;
import com.gangoffive.birdtradingplatform.service.NotificationService;
import com.gangoffive.birdtradingplatform.service.ShopOwnerService;
import com.gangoffive.birdtradingplatform.util.*;
import com.gangoffive.birdtradingplatform.wrapper.PageNumberWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class ShopOwnerServiceImpl implements ShopOwnerService {
    private final ShopOwnerRepository shopOwnerRepository;
    private final ShopOwnerMapper shopOwnerMapper;
    private final ChannelService channelService;
    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final JwtService jwtService;
    private final AppProperties appProperties;
    private final ShopStaffRepository shopStaffRepository;
    private final ShopStaffMapper shopStaffMapper;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReviewRepository reviewRepository;
    private final NotificationService notificationService;


    @Override
    public long getAccountIdByShopId(long shopId) {
        var shop = shopOwnerRepository.findById(shopId);
        if (shop.isPresent()) {
            Account acc = shop.get().getAccount();
            if (acc != null) {
                return acc.getId();
            }
        } else {
            throw new CustomRuntimeException("400", String.format("Cannot found shop with id : %d", shopId));
        }
        return 0;
    }


    @Override
    public List<LineChartDto> getDataLineChart(Long dateFrom, int date) {
        Date newDateFrom;
        if (dateFrom == null) {
            // Get the current date
            LocalDate currentDate = LocalDate.now();

            // Get the date of the previous week
            LocalDate previousWeekDate = currentDate.minusDays(date);

            // Get the start and end dates of the previous week
            newDateFrom = Date.from(previousWeekDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } else {
            try {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy");
                newDateFrom = simpleDateFormat.parse(simpleDateFormat.format(new Date(dateFrom)));
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        Optional<Account> account = accountRepository.findByEmail(email);
        List<LineChartDto> lineChartDtoList = new ArrayList<>();
        LineChartDto lineChartDtoOfBird = LineChartDto.builder()
                .id(Bird.class.getSimpleName())
                .data(dataLineChartByTypeProduct(account.get(), Bird.class, newDateFrom))
                .build();
        lineChartDtoList.add(lineChartDtoOfBird);
        LineChartDto lineChartDtoOfAccessory = LineChartDto.builder()
                .id(Accessory.class.getSimpleName())
                .data(dataLineChartByTypeProduct(account.get(), Accessory.class, newDateFrom))
                .build();
        lineChartDtoList.add(lineChartDtoOfAccessory);
        LineChartDto lineChartDtoOfFood = LineChartDto.builder()
                .id(Food.class.getSimpleName())
                .data(dataLineChartByTypeProduct(account.get(), Food.class, newDateFrom))
                .build();
        lineChartDtoList.add(lineChartDtoOfFood);
        return lineChartDtoList;
    }

    @Override
    public List<PieChartDto> getDataPieChart() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        Optional<Account> account = accountRepository.findByEmail(email);
        List<PieChartDto> pieChartDtoList = new ArrayList<>();
        PieChartDto pieChartDtoOfBird = PieChartDto.builder()
                .id(Bird.class.getSimpleName())
                .label(Bird.class.getSimpleName())
                .color(ColorChart.BIRD.getColor())
                .value(dataPieChartByTypeProduct(account.get(), Bird.class))
                .build();
        pieChartDtoList.add(pieChartDtoOfBird);
        PieChartDto pieChartDtoOfAccessory = PieChartDto.builder()
                .id(Accessory.class.getSimpleName())
                .label(Accessory.class.getSimpleName())
                .color(ColorChart.ACCESSORY.getColor())
                .value(dataPieChartByTypeProduct(account.get(), Accessory.class))
                .build();
        pieChartDtoList.add(pieChartDtoOfAccessory);
        PieChartDto pieChartDtoOfFood = PieChartDto.builder()
                .id(Food.class.getSimpleName())
                .label(Food.class.getSimpleName())
                .color(ColorChart.FOOD.getColor())
                .value(dataPieChartByTypeProduct(account.get(), Food.class))
                .build();
        pieChartDtoList.add(pieChartDtoOfFood);
        return pieChartDtoList;
    }


    @Override
    public DataBarChartDto dataBarChartByPriceAllTypeProduct() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        Optional<Account> account = accountRepository.findByEmail(email);
        List<BarChartDto> barChartDtoPreviousOneWeekList;
        List<BarChartOneTypeDto> barChartFoodPreviousOneWeekDtoList =
                dataBarChartEachTypeProduct(account.get(), Food.class, true, false, false, 1);
        List<BarChartOneTypeDto> barChartBirdPreviousOneWeekDtoList =
                dataBarChartEachTypeProduct(account.get(), Bird.class, true, false, false, 1);
        List<BarChartOneTypeDto> barChartAccessoryPreviousOneWeekDtoList =
                dataBarChartEachTypeProduct(account.get(), Accessory.class, true, false, false, 1);
        barChartDtoPreviousOneWeekList = getListBarChartDto(
                barChartFoodPreviousOneWeekDtoList,
                barChartBirdPreviousOneWeekDtoList,
                barChartAccessoryPreviousOneWeekDtoList
        );
        double totalPriceOfPreviousOneWeek = 0;
        for (BarChartDto barChartDto : barChartDtoPreviousOneWeekList) {
            totalPriceOfPreviousOneWeek += barChartDto.getAccessories() + barChartDto.getBirds() + barChartDto.getFoods();
        }

        List<BarChartDto> barChartDtoPreviousTwoWeekList;
        List<BarChartOneTypeDto> barChartFoodDtoPreviousTwoWeekList =
                dataBarChartEachTypeProduct(account.get(), Food.class, true, false, false, 2);
        List<BarChartOneTypeDto> barChartBirdDtoPreviousTwoWeekList =
                dataBarChartEachTypeProduct(account.get(), Bird.class, true, false, false, 2);
        List<BarChartOneTypeDto> barChartAccessoryDtoPreviousTwoWeekList =
                dataBarChartEachTypeProduct(account.get(), Accessory.class, true, false, false, 2);
        barChartDtoPreviousTwoWeekList = getListBarChartDto(
                barChartFoodDtoPreviousTwoWeekList,
                barChartBirdDtoPreviousTwoWeekList,
                barChartAccessoryDtoPreviousTwoWeekList
        );
        double totalPriceOfPreviousTwoWeek = 0;
        for (BarChartDto barChartDto : barChartDtoPreviousTwoWeekList) {
            totalPriceOfPreviousTwoWeek += barChartDto.getAccessories() + barChartDto.getBirds() + barChartDto.getFoods();
        }
        double percent = ((totalPriceOfPreviousOneWeek - totalPriceOfPreviousTwoWeek)
                / (totalPriceOfPreviousOneWeek + totalPriceOfPreviousTwoWeek)) * 100;
        DecimalFormat decimalFormat = new DecimalFormat("#.00");
        String formattedPercent = decimalFormat.format(percent);
        String formattedTotalPrice = decimalFormat.format(totalPriceOfPreviousOneWeek);

        DataBarChartDto dataBarChartDto = DataBarChartDto.builder()
                .barChartDtoList(barChartDtoPreviousOneWeekList)
                .total(Double.parseDouble(formattedTotalPrice))
                .percent(Double.parseDouble(formattedPercent))
                .build();
        return dataBarChartDto;
    }

    @Override
    public DataBarChartDto dataBarChartByOrderAllTypeProduct() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        Optional<Account> account = accountRepository.findByEmail(email);
        List<BarChartDto> barChartDtoPreviousOneWeekList;
        List<BarChartOneTypeDto> barChartFoodPreviousOneWeekDtoList =
                dataBarChartEachTypeProduct(account.get(), Food.class, false, true, false, 1);
        List<BarChartOneTypeDto> barChartBirdPreviousOneWeekDtoList =
                dataBarChartEachTypeProduct(account.get(), Bird.class, false, true, false, 1);
        List<BarChartOneTypeDto> barChartAccessoryPreviousOneWeekDtoList =
                dataBarChartEachTypeProduct(account.get(), Accessory.class, false, true, false, 1);
        barChartDtoPreviousOneWeekList = getListBarChartDto(
                barChartFoodPreviousOneWeekDtoList,
                barChartBirdPreviousOneWeekDtoList,
                barChartAccessoryPreviousOneWeekDtoList);
        double totalOrderOfPreviousOneWeek = 0;
        for (BarChartDto barChartDto : barChartDtoPreviousOneWeekList) {
            totalOrderOfPreviousOneWeek += barChartDto.getAccessories() + barChartDto.getBirds() + barChartDto.getFoods();
        }

        List<BarChartDto> barChartDtoPreviousTwoWeekList;
        List<BarChartOneTypeDto> barChartFoodDtoPreviousTwoWeekList =
                dataBarChartEachTypeProduct(account.get(), Food.class, false, true, false, 2);
        List<BarChartOneTypeDto> barChartBirdDtoPreviousTwoWeekList =
                dataBarChartEachTypeProduct(account.get(), Bird.class, false, true, false, 2);
        List<BarChartOneTypeDto> barChartAccessoryDtoPreviousTwoWeekList =
                dataBarChartEachTypeProduct(account.get(), Accessory.class, false, true, false, 2);
        barChartDtoPreviousTwoWeekList = getListBarChartDto(
                barChartFoodDtoPreviousTwoWeekList,
                barChartBirdDtoPreviousTwoWeekList,
                barChartAccessoryDtoPreviousTwoWeekList
        );
        double totalOrderOfPreviousTwoWeek = 0;
        for (BarChartDto barChartDto : barChartDtoPreviousTwoWeekList) {
//            log.info("barChartDto.getAccessories() {}", barChartDto.getAccessories());
//            log.info("barChartDto.getBirds() {}", barChartDto.getBirds());
//            log.info("barChartDto.getFoods() {}", barChartDto.getFoods());
            totalOrderOfPreviousTwoWeek += barChartDto.getAccessories() + barChartDto.getBirds() + barChartDto.getFoods();
        }
//        log.info("totalOrderOfPreviousTwoWeek {}", totalOrderOfPreviousTwoWeek);
        double percent = ((totalOrderOfPreviousOneWeek - totalOrderOfPreviousTwoWeek)
                / (totalOrderOfPreviousTwoWeek + totalOrderOfPreviousOneWeek)) * 100;
        DecimalFormat decimalFormat = new DecimalFormat("#.00");

        String formattedPercent = decimalFormat.format(percent);
        String formattedTotalPrice = decimalFormat.format(totalOrderOfPreviousOneWeek);

        DataBarChartDto dataBarChartDto = DataBarChartDto.builder()
                .barChartDtoList(barChartDtoPreviousOneWeekList)
                .total(Double.parseDouble(formattedTotalPrice))
                .percent(Double.parseDouble(formattedPercent))
                .build();
        return dataBarChartDto;
    }

    @Override
    public DataBarChartDto dataBarChartByReviewAllTypeProduct() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        Optional<Account> account = accountRepository.findByEmail(email);
        List<BarChartDto> barChartDtoPreviousOneWeekList;
        List<BarChartOneTypeDto> barChartFoodPreviousOneWeekDtoList =
                dataBarChartEachTypeProduct(account.get(), Food.class, false, false, true, 1);
        List<BarChartOneTypeDto> barChartBirdPreviousOneWeekDtoList =
                dataBarChartEachTypeProduct(account.get(), Bird.class, false, false, true, 1);
        List<BarChartOneTypeDto> barChartAccessoryPreviousOneWeekDtoList =
                dataBarChartEachTypeProduct(account.get(), Accessory.class, false, false, true, 1);
        barChartDtoPreviousOneWeekList = getListBarChartDto(
                barChartFoodPreviousOneWeekDtoList,
                barChartBirdPreviousOneWeekDtoList,
                barChartAccessoryPreviousOneWeekDtoList);
        double totalReviewOfPreviousOneWeek = 0;
        for (BarChartDto barChartDto : barChartDtoPreviousOneWeekList) {
            totalReviewOfPreviousOneWeek += barChartDto.getAccessories() + barChartDto.getBirds() + barChartDto.getFoods();
        }

        List<BarChartDto> barChartDtoPreviousTwoWeekList;
        List<BarChartOneTypeDto> barChartFoodDtoPreviousTwoWeekList =
                dataBarChartEachTypeProduct(account.get(), Food.class, false, false, true, 2);
        List<BarChartOneTypeDto> barChartBirdDtoPreviousTwoWeekList =
                dataBarChartEachTypeProduct(account.get(), Bird.class, false, false, true, 2);
        List<BarChartOneTypeDto> barChartAccessoryDtoPreviousTwoWeekList =
                dataBarChartEachTypeProduct(account.get(), Accessory.class, false, false, true, 2);
        barChartDtoPreviousTwoWeekList = getListBarChartDto(
                barChartFoodDtoPreviousTwoWeekList,
                barChartBirdDtoPreviousTwoWeekList,
                barChartAccessoryDtoPreviousTwoWeekList
        );
        double totalReviewOfPreviousTwoWeek = 0;
        for (BarChartDto barChartDto : barChartDtoPreviousTwoWeekList) {
//            log.info("barChartDto.getAccessories() {}", barChartDto.getAccessories());
//            log.info("barChartDto.getBirds() {}", barChartDto.getBirds());
//            log.info("barChartDto.getFoods() {}", barChartDto.getFoods());
            totalReviewOfPreviousTwoWeek += barChartDto.getAccessories() + barChartDto.getBirds() + barChartDto.getFoods();
        }
//        log.info("totalReviewOfPreviousOneWeek {}", totalReviewOfPreviousOneWeek);
//        log.info("totalReviewOfPreviousTwoWeek {}", totalReviewOfPreviousTwoWeek);
        double percent = ((totalReviewOfPreviousOneWeek - totalReviewOfPreviousTwoWeek)
                / (totalReviewOfPreviousTwoWeek + totalReviewOfPreviousOneWeek)) * 100;
        DecimalFormat decimalFormat = new DecimalFormat("#.00");
//        log.info("percent {}", percent);
        String formattedPercent = decimalFormat.format(percent);
        String formattedTotalReview = decimalFormat.format(totalReviewOfPreviousOneWeek);

        DataBarChartDto dataBarChartDto = DataBarChartDto.builder()
                .barChartDtoList(barChartDtoPreviousOneWeekList)
                .total(Double.parseDouble(formattedTotalReview))
                .percent(Double.parseDouble(formattedPercent))
                .build();
        return dataBarChartDto;
    }


    private double dataPieChartByTypeProduct(Account account, Class<?> productClass) {
        List<BarChartOneTypeDto> barChartFoodPreviousOneWeekDtoList =
                dataBarChartEachTypeProduct(account, productClass, true, false, false, 1);
        double sum = barChartFoodPreviousOneWeekDtoList.stream().mapToDouble(BarChartOneTypeDto::getValue).sum();
        DecimalFormat decimalFormat = new DecimalFormat("#.00");
        String formatSum = decimalFormat.format(sum);
        return Double.parseDouble(formatSum);
    }

    private List<BarChartDto> getListBarChartDto(
            List<BarChartOneTypeDto> barChartFoodDtoList,
            List<BarChartOneTypeDto> barChartBirdDtoList,
            List<BarChartOneTypeDto> barChartAccessoryDtoList
    ) {
        List<BarChartDto> barChartDtoList = new ArrayList<>();
        for (int i = 0; i < barChartFoodDtoList.size(); i++) {
            BarChartDto barChartDto = BarChartDto.builder()
                    .date(barChartFoodDtoList.get(i).getDate())
                    .accessories(barChartAccessoryDtoList.get(i).getValue())
                    .colorAccessories(barChartAccessoryDtoList.get(i).getColor())
                    .birds(barChartBirdDtoList.get(i).getValue())
                    .colorBirds(barChartBirdDtoList.get(i).getColor())
                    .foods(barChartFoodDtoList.get(i).getValue())
                    .colorFoods(barChartFoodDtoList.get(i).getColor())
                    .build();
            barChartDtoList.add(barChartDto);
        }
//        for (BarChartDto barChartDto : barChartDtoList) {
//            log.info("barChartDto {}", barChartDto);
//        }
        return barChartDtoList;
    }

    public List<BarChartOneTypeDto> dataBarChartEachTypeProduct(
            Account account, Class<?> productClass,
            boolean isCalcPrice, boolean isCalcQuantity, boolean isCalcReview, int week
    ) {
        List<BarChartOneTypeDto> barChartOneTypeDtoList = new ArrayList<>();
        List<LocalDate> dateList = DateUtils.getAllDatePreviousWeek(week);
        List<Order> orderList = getAllOrdersNumberPreviousWeek(account, week);
        //Get list OrderDetail of list Order
        List<OrderDetail> orderDetails = orderDetailRepository.findOrderDetailByOrderIn(orderList);

        //Get OrderDetail of Product have instance of Food
        List<OrderDetail> listOrderDetailOfProduct = orderDetails.stream()
                .filter(
                        orderDetail -> productClass.isInstance(orderDetail.getProduct())
                ).toList();

        List<Order> listOrderOfProduct = listOrderDetailOfProduct.stream().map(OrderDetail::getOrder).distinct().toList();
        int countDate = 0;
        for (LocalDate date : dateList) {
            countDate++;
            double totalPrice = 0;
            double totalQuantity = 0;
            double totalReview = 0;
            if (isCalcPrice || isCalcQuantity) {
                for (Order order : listOrderOfProduct) {
                    if (order.getCreatedDate().toInstant().atZone(ZoneId.of("Asia/Bangkok")).toLocalDate().equals(date)) {
//                        log.info("order.getCreatedDate().toInstant().atZone(ZoneId.of(\"Asia/Bangkok\")) {}", order.getCreatedDate().toInstant().atZone(ZoneId.of("Asia/Bangkok")));
                        for (OrderDetail orderDetail : listOrderDetailOfProduct) {
                            if (orderDetail.getOrder().equals(order)) {
                                if (isCalcPrice) {
                                    totalPrice += orderDetail.getPrice() * orderDetail.getQuantity();
                                }
                                if (isCalcQuantity) {
                                    totalQuantity++;
                                }
                            }
                        }
                    }
                }
            }

            if (isCalcReview) {
                Optional<List<Review>> reviews = reviewRepository.findAllByReviewDateBetweenAndOrderDetail_Product_ShopOwner(
                        Date.from(date.atStartOfDay(ZoneId.of("Asia/Bangkok")).toInstant()),
                        Date.from(date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Bangkok")).toInstant()), account.getShopOwner()
                );
                List<OrderDetail> orderDetailList = reviews.get().stream().map(Review::getOrderDetail).toList();
                List<OrderDetail> listOrderDetailFilter = orderDetailList.stream()
                        .filter(
                                orderDetail -> productClass.isInstance(orderDetail.getProduct())
                        ).toList();
//                log.info("dateFrom {}", Date.from(date.atStartOfDay(ZoneId.of("Asia/Bangkok")).toInstant()));
//                log.info("dateTo {}", Date.from(date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Bangkok")).toInstant()));
//                log.info("review {}", reviews.get().size());
//                log.info("class {}", productClass.getName());
                if (reviews.isPresent()) {
                    totalReview = listOrderDetailFilter.size();
                }
            }

            DecimalFormat decimalFormat = new DecimalFormat("#.00");
            String formattedTotalPrice = decimalFormat.format(totalPrice);
//            log.info("formattedTotalPrice {}", formattedTotalPrice);
            BarChartOneTypeDto barChartDto = new BarChartOneTypeDto();
            if (isCalcPrice) {
                barChartDto.setValue(Double.parseDouble(formattedTotalPrice));
            }
            if (isCalcQuantity) {
                barChartDto.setValue(totalQuantity);
            }
            if (isCalcReview) {
                barChartDto.setValue(totalReview);
            }

            if (countDate == 1) {
                barChartDto.setDate(DayOfWeek.MONDAY.name().substring(0, 1).toUpperCase()
                        + DayOfWeek.MONDAY.name().toLowerCase().substring(1, 3));
            } else if (countDate == 2) {
                barChartDto.setDate(DayOfWeek.TUESDAY.name().substring(0, 1).toUpperCase()
                        + DayOfWeek.TUESDAY.name().toLowerCase().substring(1, 3));
            } else if (countDate == 3) {
                barChartDto.setDate(DayOfWeek.WEDNESDAY.name().substring(0, 1).toUpperCase()
                        + DayOfWeek.WEDNESDAY.name().toLowerCase().substring(1, 3));
            } else if (countDate == 4) {
                barChartDto.setDate(DayOfWeek.THURSDAY.name().substring(0, 1).toUpperCase()
                        + DayOfWeek.THURSDAY.name().toLowerCase().substring(1, 3));
            } else if (countDate == 5) {
                barChartDto.setDate(DayOfWeek.FRIDAY.name().substring(0, 1).toUpperCase()
                        + DayOfWeek.FRIDAY.name().toLowerCase().substring(1, 3));
            } else if (countDate == 6) {
                barChartDto.setDate(DayOfWeek.SATURDAY.name().substring(0, 1).toUpperCase()
                        + DayOfWeek.SATURDAY.name().toLowerCase().substring(1, 3));
            } else if (countDate == 7) {
                barChartDto.setDate(DayOfWeek.SUNDAY.name().substring(0, 1).toUpperCase()
                        + DayOfWeek.SUNDAY.name().toLowerCase().substring(1, 3));
            }

            if (productClass.equals(Food.class)) {
                barChartDto.setColor(ColorChart.FOOD.getColor());
            } else if (productClass.equals(Accessory.class)) {
                barChartDto.setColor(ColorChart.ACCESSORY.getColor());
            } else if (productClass.equals(Bird.class)) {
                barChartDto.setColor(ColorChart.BIRD.getColor());
            }
            barChartOneTypeDtoList.add(barChartDto);
        }
        return barChartOneTypeDtoList;
    }

    @Override
    public ResponseEntity<?> redirectToShopOwner() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        Optional<Account> account = accountRepository.findByEmail(username);
        if (account.get().getRole().equals(UserRole.SHOPOWNER)) {
            if (account.get().getShopOwner().getStatus().equals(ShopOwnerStatus.ACTIVE)) {
                String token = jwtService.generateTokenShopOwner(UserPrincipal.create(account.get()), account.get().getShopOwner().getId());
                SuccessResponse successResponse = SuccessResponse.builder()
                        .successCode(String.valueOf(HttpStatus.OK.value()))
                        .successMessage("get-token?token=" + token)
                        .build();
                return new ResponseEntity<>(successResponse, HttpStatus.OK);
            } else {
                return ResponseUtils.getErrorResponseLocked("Your shop account is ban.");
            }
        } else {
            return ResponseUtils.getErrorResponseBadRequest("You don't have permission to access.");
        }
    }

    @Override
    public ResponseEntity<?> getShopInfoByUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("email {}", email);
//        email = "YamamotoEmi37415@gmail.com"; //just for test after must delete
        var account = accountRepository.findByEmail(email);
        if (account.isPresent()) {
            var shopInfo = account.get().getShopOwner();
            if (shopInfo != null) {
                ShopInfoDto shopOwnerDto = shopOwnerMapper.modelToShopInfoDto(shopInfo);
                return ResponseEntity.ok(shopOwnerDto);
            } else {
                return ResponseEntity.ok(ErrorResponse.builder().errorCode(ResponseCode.THIS_ACCOUNT_NOT_HAVE_SHOP.getCode() + "")
                        .errorMessage(ResponseCode.THIS_ACCOUNT_NOT_HAVE_SHOP.getMessage()).build());
            }
        } else {
            throw new CustomRuntimeException("400", "Some thing went wrong");
        }

    }

    @Override
    public long getShopIdByEmail(String email) {
        var acc = accountRepository.findByEmail(email);
        if (acc.isPresent()) {
            if (acc.get().getShopOwner() != null) {
                return acc.get().getShopOwner().getId();
            }
        }
        return 0;
    }


    public List<Order> getAllOrdersNumberPreviousWeek(Account account, int week) {
        // Get the current date
        LocalDate currentDate = LocalDate.now();

        // Get the date of the previous week
        LocalDate previousWeekDate = currentDate.minusWeeks(week);

        // Get the start and end dates of the previous week
        LocalDate previousWeekStartDate = previousWeekDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate previousWeekEndDate = previousWeekDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusDays(1);

        //Get list Order of Shop Owner
//        LocalDate previousWeekEndDate = previousWeekDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
//        List<Order> tmpOrders = orderRepository.findByShopOwner(account.getShopOwner());
//        List<Order> orders = tmpOrders.stream()
//                .filter(
//                        order ->
//                                (order.getCreatedDate().toInstant().atZone(ZoneId.of("Asia/Bangkok")).toLocalDate().equals(previousWeekStartDate)
//                                || order.getCreatedDate().toInstant().atZone(ZoneId.of("Asia/Bangkok")).toLocalDate().isAfter(previousWeekStartDate))
//                                && (order.getCreatedDate().toInstant().atZone(ZoneId.of("Asia/Bangkok")).toLocalDate().equals(previousWeekEndDate)
//                                || order.getCreatedDate().toInstant().atZone(ZoneId.of("Asia/Bangkok")).toLocalDate().isBefore(previousWeekEndDate))
//                )
//                .collect(Collectors.toList());

        List<Order> orders = orderRepository.findByShopOwnerAndCreatedDateBetween(
                account.getShopOwner(),
                Date.from(previousWeekStartDate.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                Date.from(previousWeekEndDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        return orders;
    }

//    private double dataPieChartByTypeProduct(Account account, Class<?> productClass) throws ParseException {
//        List<LocalDate> dateList = getAllDatePreviousWeek(1);
//        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
//        String formattedDateFrom = dateList.get(0).format(outputFormatter);
//        String formattedDateTo = dateList.get(6).format(outputFormatter);
//
//        Date dateFrom = new SimpleDateFormat("dd/MM/yyyy").parse(formattedDateFrom);
//        Date dateTo= new SimpleDateFormat("dd/MM/yyyy").parse(formattedDateTo);
//        log.info("dateFrom {}", dateFrom);
//        log.info("dateTo {}", dateTo);
//        //Get list Orders before now 7 day
//        List<Order> orders = getAllOrdersNumberPreviousWeek(account, 1);
//        for (Order order : orders) {
//            log.info("order.getId() {}", order.getId());
//            log.info("order.getCreatedDate() {}", order.getCreatedDate());
//        }
//
//        //Get list OrderDetail of list Order
//        List<OrderDetail> orderDetails = orderDetailRepository.findOrderDetailByOrderIn(orders);
//
//        List<OrderDetail> listOrderDetailOfProduct = orderDetails.stream()
//                .filter(
////                        orderDetail -> orderDetail.getProduct() instanceof Food
//                        orderDetail -> productClass.isInstance(orderDetail.getProduct())
//                ).toList();
//
//        for (OrderDetail order : listOrderDetailOfProduct) {
//            log.info("order.getId() {}", order.getId());
//            log.info("order.getOrder().getId() {}", order.getOrder().getId());
//            log.info("order.getCreatedDate() {}", order.getProduct());
//        }
//
//        double totalPrice = listOrderDetailOfProduct.stream()
//                .mapToDouble(orderDetail -> orderDetail.getPrice() * orderDetail.getQuantity()).sum();
//        DecimalFormat decimalFormat = new DecimalFormat("#.00");
//        String formattedTotalPrice = decimalFormat.format(totalPrice);
//        log.info("totalPrice {}", totalPrice);
//        return Double.parseDouble(formattedTotalPrice);
//    }

    private List<DataLineChartDto> dataLineChartByTypeProduct(Account account, Class<?> productClass, Date dateFrom) {
        //Get list Order of Shop Owner
        List<Order> tmpOrders = orderRepository.findByShopOwner(account.getShopOwner());
        List<Order> orders = tmpOrders.stream().filter(order -> order.getCreatedDate().after(dateFrom)).collect(Collectors.toList());
//        for (Order order : orders) {
//            log.info("id {}", order.getId());
//        }

        //Get list OrderDetail of list Order
        List<OrderDetail> orderDetails = orderDetailRepository.findOrderDetailByOrderIn(orders);
//        for (OrderDetail orderDetail : orderDetails) {
//            log.info("id od {}", orderDetail.getId());
//        }

        //Get OrderDetail of Product have instance of Food
        List<OrderDetail> listOrderDetailOfProduct = orderDetails.stream()
                .filter(
//                        orderDetail -> orderDetail.getProduct() instanceof Food
                        orderDetail -> productClass.isInstance(orderDetail.getProduct())
                ).toList();
//        log.info("size od Food {}", listOrderDetailOfProduct.size());
//        for (OrderDetail orderDetail : listOrderDetailOfProduct) {
//            log.info("id od Food {}", orderDetail.getId());
//        }

        //Get list Order of Food From orderDetailOfFoods
        List<Order> listOrderOfProduct = listOrderDetailOfProduct.stream().map(OrderDetail::getOrder).distinct().toList();
//        log.info("size o Food {}", listOrderOfProduct.size());
//        for (Order order : listOrderOfProduct) {
//            log.info("id o Food {}", order.getId());
//        }


        //Distinct date of orderOfBirds to get list LocalDate
//        List<LocalDate> listDistinctDateOfProduct = listOrderOfProduct.stream()
//                .map(order -> order.getCreatedDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate())
//                .distinct()
//                .collect(Collectors.toList());
//        for (LocalDate date : listDistinctDateOfProduct) {
//            log.info("distinctDateOfFoods {}", date);
//        }

        List<LocalDate> listDistinctDateOfProduct = new ArrayList<>();
        LocalDate now = LocalDate.now();
//        LocalDate now = LocalDate.of(2023, 06, 22);

        LocalDate currentDate = dateFrom.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        log.info("currentDate {}", currentDate);
        while (!currentDate.isAfter(now)) {
            listDistinctDateOfProduct.add(currentDate);
            currentDate = currentDate.plusDays(1);
        }
        List<DataLineChartDto> dataLineChartDtoListOfProduct = new ArrayList<>();
        for (LocalDate date : listDistinctDateOfProduct) {
            //One day have many orders
            double totalPrice = 0;
            for (Order order : listOrderOfProduct) {
                //One order have many OrderDetails
//                log.info("order id {}", order.getId());
//                log.info("order.getCreatedDate() {}", order.getCreatedDate());
//                log.info("date {}", date);
                if (order.getCreatedDate().toInstant().atZone(ZoneId.of("Asia/Bangkok")).toLocalDate().equals(date)) {
//                    log.info("order id {}", order.getId());

                    for (OrderDetail orderDetail : listOrderDetailOfProduct) {
//                        log.info("orderDetail id {}", orderDetail.getId());
                        if (orderDetail.getOrder().equals(order)) {
//                            log.info("orderDetail.getPrice() {}, orderDetail.getQuantity() {}", orderDetail.getPrice(), orderDetail.getQuantity());
                            totalPrice += orderDetail.getPrice() * orderDetail.getQuantity();
//                            log.info("total {}", totalPrice);
                        }
                    }
                }
            }
            DecimalFormat decimalFormat = new DecimalFormat("#.00");
            String formattedTotalPrice = decimalFormat.format(totalPrice);
            DataLineChartDto dataLineChartDto = DataLineChartDto.builder()
                    .x(DateUtils.formatLocalDateToString(date))
                    .y(Double.parseDouble(formattedTotalPrice))
                    .build();
            dataLineChartDtoListOfProduct.add(dataLineChartDto);
        }
//        for (DataLineChartDto dataLineChartDto : dataLineChartDtoListOfProduct) {
//            log.info("dataLineChartDto {}", dataLineChartDto);
//        }
        return dataLineChartDtoListOfProduct;
    }

    @Override
    public ResponseEntity<?> createAccountStaff(CreateAccountSaffDto createAccountSaffDto) {
        if (createAccountSaffDto.getConfirmPassword().equals(createAccountSaffDto.getPassword())) {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            Optional<Account> accountShop = accountRepository.findByEmail(email);
            ShopOwner shopOwner = accountShop.get().getShopOwner();
            if (shopOwner != null) {
                Optional<ShopStaff> accountStaff = shopStaffRepository
                        .findByUserNameAndShopOwner_Id(createAccountSaffDto.getUserName(), accountShop.get().getId());
                if (!accountStaff.isPresent()) {
                    ShopStaff shopStaff = ShopStaff.builder()
                            .userName(createAccountSaffDto.getUserName())
                            .password(passwordEncoder.encode(createAccountSaffDto.getPassword()))
                            .shopOwner(shopOwner)
                            .status(AccountStatus.VERIFY)
                            .build();
                    ShopStaff saveShopStaff = shopStaffRepository.save(shopStaff);
                    SuccessResponse successResponse = SuccessResponse.builder()
                            .successCode(String.valueOf(HttpStatus.CREATED.value()))
                            .successMessage("Create shop staff successfully.")
                            .build();
                    return new ResponseEntity<>(successResponse, HttpStatus.CREATED);
                } else {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .errorCode(String.valueOf(HttpStatus.CONFLICT))
                            .errorMessage("Username already exists.")
                            .build();
                    return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
                }
            } else {
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .errorCode(String.valueOf(HttpStatus.CONFLICT))
                        .errorMessage("Shop is not exists.")
                        .build();
                return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
            }

        } else {
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.BAD_REQUEST))
                    .errorMessage("confirm password does not match")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }
    }

    public ResponseEntity<?> getShopStaff(int pageNumber) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<Account> account = accountRepository.findByEmail(email);
        ShopOwner shopOwner = account.get().getShopOwner();
        if (shopOwner != null) {
            if (pageNumber > 0) {
                pageNumber--;
            }
            PageRequest pageRequest = PageRequest.of(pageNumber, PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE);
            Page<ShopStaff> lists = shopStaffRepository.findByShopOwner(shopOwner, pageRequest);
            if (lists != null && lists.getContent().size() != 0) {
                List<ShopStaffDto> listShopStaff = lists.stream().map(shopStaffMapper::modelToDto).toList();
                PageNumberWrapper result = new PageNumberWrapper();
                result.setLists(listShopStaff);
                result.setPageNumber(lists.getTotalPages());
                result.setTotalElement(lists.getTotalElements());
                return ResponseEntity.ok(result);
            } else {
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                        .errorMessage("No list staff of shop " + shopOwner.getShopName() + ".")
                        .build();
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
            }

        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                .errorMessage("Account " + account.get().getFullName() + " no shop.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @Override
    public ResponseEntity<?> updateShopOwnerProfile(MultipartFile avatarImg, MultipartFile coverImg, ShopOwnerUpdateDto shopUpdateDto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        var shop = shopOwnerRepository.findByAccount_Email(email);
        if (shop.isPresent()) {
            ShopOwner shopUpdate = shop.get();
            shopUpdate.setShopName(shopUpdateDto.getShopName());
            shopUpdate.setShopPhone(shopUpdateDto.getShopPhone());
            shopUpdate.setDescription(shopUpdateDto.getDescription());
            try {
                String avatar = this.uploadImages(avatarImg);
                String cover = this.uploadImages(coverImg);
                if (!avatar.isEmpty())
                    shopUpdate.setAvatarImgUrl(avatar);
                if (!cover.isEmpty())
                    shopUpdate.setCoverImgUrl(cover);
                Address address = shopUpdate.getAddress();
                address.setAddress(shopUpdateDto.getAddress());
                addressRepository.save(address);
                shopUpdate = shopOwnerRepository.save(shopUpdate);
                ShopInfoDto shopInfoDto = shopOwnerMapper.modelToShopInfoDto(shopUpdate);
                return ResponseEntity.ok(shopInfoDto);
            } catch (Exception e) {
                e.printStackTrace();
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                        .errorMessage("Upload file fail")
                        .build();
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
            }
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                .errorMessage("Some thing went wrong!")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @Override
    public ResponseEntity<?> filterAllShopOwner(ShopOwnerAccountFilterDto shopOwnerAccountFilter) {
        if (shopOwnerAccountFilter.getPageNumber() > 0) {
            int pageNumber = shopOwnerAccountFilter.getPageNumber() - 1;
            PageRequest pageRequest = PageRequest.of(pageNumber, PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE);
            PageRequest pageRequestWithSort = null;
            if (shopOwnerAccountFilter.getSortDirection() != null
                    && !shopOwnerAccountFilter.getSortDirection().getSort().isEmpty()
                    && !shopOwnerAccountFilter.getSortDirection().getField().isEmpty()
            ) {
                if (
                        !SortShopOwnerAccountColumn.checkField(shopOwnerAccountFilter.getSortDirection().getField())
                ) {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                            .errorMessage("Not found this field in sort direction.")
                            .build();
                    return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
                }
                if (shopOwnerAccountFilter.getSortDirection().getSort().toUpperCase().equals(Sort.Direction.ASC.name())) {
                    pageRequestWithSort = getPageRequest(shopOwnerAccountFilter, pageNumber, Sort.Direction.ASC);
                } else if (shopOwnerAccountFilter.getSortDirection().getSort().toUpperCase().equals(Sort.Direction.DESC.name())) {
                    pageRequestWithSort = getPageRequest(shopOwnerAccountFilter, pageNumber, Sort.Direction.DESC);
                } else {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                            .errorMessage("Not found this direction.")
                            .build();
                    return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
                }
            }

            if (
                    shopOwnerAccountFilter.getShopOwnerSearchInfo().getField().isEmpty()
                            && shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue().isEmpty()
                            && shopOwnerAccountFilter.getShopOwnerSearchInfo().getOperator().isEmpty()
                            && shopOwnerAccountFilter.getSortDirection().getField().isEmpty()
                            && shopOwnerAccountFilter.getSortDirection().getSort().isEmpty()
            ) {
                pageRequestWithSort = PageRequest.of(
                        pageNumber,
                        PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE,
                        Sort.by(Sort.Direction.DESC,
                                SortShopOwnerAccountColumn.CREATED_DATE.getColumn()
                        )
                );
                return filterAllShopOwnerAccountAllFieldEmpty(pageRequestWithSort);
            } else if (
                    shopOwnerAccountFilter.getShopOwnerSearchInfo().getField().isEmpty()
                            && shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue().isEmpty()
                            && shopOwnerAccountFilter.getShopOwnerSearchInfo().getOperator().isEmpty()
                            && !shopOwnerAccountFilter.getSortDirection().getField().isEmpty()
                            && !shopOwnerAccountFilter.getSortDirection().getSort().isEmpty()
            ) {
                return filterAllShopOwnerAccountAllFieldEmpty(pageRequestWithSort);
            }

            if (
                    shopOwnerAccountFilter.getShopOwnerSearchInfo().getField().equals(FieldShopOwnerAccountTable.ID.getField())
                            && !shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue().isEmpty()
            ) {
                if (shopOwnerAccountFilter.getShopOwnerSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterShopOwnerAccountByIdEqual(shopOwnerAccountFilter, pageRequest);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    shopOwnerAccountFilter.getShopOwnerSearchInfo().getField().equals(FieldShopOwnerAccountTable.EMAIL.getField())
                            && !shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue().isEmpty()
                            && shopOwnerAccountFilter.getSortDirection().getField().isEmpty()
                            && shopOwnerAccountFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (shopOwnerAccountFilter.getShopOwnerSearchInfo().getOperator().equals(Operator.CONTAIN.getOperator())) {
                    return filterShopOwnerAccountByEmailContain(shopOwnerAccountFilter, pageRequest);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    shopOwnerAccountFilter.getShopOwnerSearchInfo().getField().equals(FieldShopOwnerAccountTable.EMAIL.getField())
                            && !shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue().isEmpty()
            ) {
                if (shopOwnerAccountFilter.getShopOwnerSearchInfo().getOperator().equals(Operator.CONTAIN.getOperator())) {
                    return filterShopOwnerAccountByEmailContain(shopOwnerAccountFilter, pageRequestWithSort);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    shopOwnerAccountFilter.getShopOwnerSearchInfo().getField().equals(FieldShopOwnerAccountTable.SHOP_NAME.getField())
                            && !shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue().isEmpty()
                            && shopOwnerAccountFilter.getSortDirection().getField().isEmpty()
                            && shopOwnerAccountFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (shopOwnerAccountFilter.getShopOwnerSearchInfo().getOperator().equals(Operator.CONTAIN.getOperator())) {
                    return filterShopOwnerAccountByShopNameContain(shopOwnerAccountFilter, pageRequest);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    shopOwnerAccountFilter.getShopOwnerSearchInfo().getField().equals(FieldShopOwnerAccountTable.SHOP_NAME.getField())
                            && !shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue().isEmpty()
            ) {
                if (shopOwnerAccountFilter.getShopOwnerSearchInfo().getOperator().equals(Operator.CONTAIN.getOperator())) {
                    return filterShopOwnerAccountByShopNameContain(shopOwnerAccountFilter, pageRequestWithSort);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    shopOwnerAccountFilter.getShopOwnerSearchInfo().getField().equals(FieldShopOwnerAccountTable.SHOP_PHONE.getField())
                            && !shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue().isEmpty()
                            && shopOwnerAccountFilter.getSortDirection().getField().isEmpty()
                            && shopOwnerAccountFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (shopOwnerAccountFilter.getShopOwnerSearchInfo().getOperator().equals(Operator.CONTAIN.getOperator())) {
                    return filterShopOwnerAccountByShopPhoneContain(shopOwnerAccountFilter, pageRequest);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    shopOwnerAccountFilter.getShopOwnerSearchInfo().getField().equals(FieldShopOwnerAccountTable.SHOP_PHONE.getField())
                            && !shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue().isEmpty()
            ) {
                if (shopOwnerAccountFilter.getShopOwnerSearchInfo().getOperator().equals(Operator.CONTAIN.getOperator())) {
                    return filterShopOwnerAccountByShopPhoneContain(shopOwnerAccountFilter, pageRequestWithSort);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    shopOwnerAccountFilter.getShopOwnerSearchInfo().getField().equals(FieldShopOwnerAccountTable.ADDRESS.getField())
                            && !shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue().isEmpty()
                            && shopOwnerAccountFilter.getSortDirection().getField().isEmpty()
                            && shopOwnerAccountFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (shopOwnerAccountFilter.getShopOwnerSearchInfo().getOperator().equals(Operator.CONTAIN.getOperator())) {
                    return filterShopOwnerAccountByAddressContain(shopOwnerAccountFilter, pageRequest);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    shopOwnerAccountFilter.getShopOwnerSearchInfo().getField().equals(FieldShopOwnerAccountTable.ADDRESS.getField())
                            && !shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue().isEmpty()
            ) {
                if (shopOwnerAccountFilter.getShopOwnerSearchInfo().getOperator().equals(Operator.CONTAIN.getOperator())) {
                    return filterShopOwnerAccountByAddressContain(shopOwnerAccountFilter, pageRequestWithSort);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    shopOwnerAccountFilter.getShopOwnerSearchInfo().getField().equals(FieldShopOwnerAccountTable.STATUS.getField())
                            && !shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue().isEmpty()
                            && shopOwnerAccountFilter.getSortDirection().getField().isEmpty()
                            && shopOwnerAccountFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (shopOwnerAccountFilter.getShopOwnerSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterShopOwnerAccountByStatusEqual(shopOwnerAccountFilter, pageRequest);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    shopOwnerAccountFilter.getShopOwnerSearchInfo().getField().equals(FieldShopOwnerAccountTable.STATUS.getField())
                            && !shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue().isEmpty()
            ) {
                if (shopOwnerAccountFilter.getShopOwnerSearchInfo().getOperator().equals(Operator.EQUAL.getOperator())) {
                    return filterShopOwnerAccountByStatusEqual(shopOwnerAccountFilter, pageRequestWithSort);
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    shopOwnerAccountFilter.getShopOwnerSearchInfo().getField().equals(FieldShopOwnerAccountTable.CREATED_DATE.getField())
                            && !shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue().isEmpty()
                            && shopOwnerAccountFilter.getSortDirection().getField().isEmpty()
                            && shopOwnerAccountFilter.getSortDirection().getSort().isEmpty()
            ) {
                if (shopOwnerAccountFilter.getShopOwnerSearchInfo().getOperator().equals(Operator.FROM_TO.getOperator())) {
                    DateRangeDto dateRange = JsonUtil.INSTANCE.getObject(shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue(), DateRangeDto.class);
                    if (dateRange.getDateTo() == -1L) {
                        return filterShopOwnerAccountByCreatedDateGreaterThanOrEqual(dateRange, pageRequest);
                    } else {
                        return filterShopOwnerAccountByCreatedDateFromTo(dateRange, pageRequest);
                    }
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            } else if (
                    shopOwnerAccountFilter.getShopOwnerSearchInfo().getField().equals(FieldShopOwnerAccountTable.CREATED_DATE.getField())
                            && !shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue().isEmpty()
            ) {
                if (shopOwnerAccountFilter.getShopOwnerSearchInfo().getOperator().equals(Operator.FROM_TO.getOperator())) {
                    DateRangeDto dateRange = JsonUtil.INSTANCE.getObject(shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue(), DateRangeDto.class);
                    if (dateRange.getDateTo() == -1L) {
                        return filterShopOwnerAccountByCreatedDateGreaterThanOrEqual(dateRange, pageRequest);
                    } else {
                        return filterShopOwnerAccountByCreatedDateFromTo(dateRange, pageRequest);
                    }
                }
                return ResponseUtils.getErrorResponseNotFoundOperator();
            }

            return null;
        } else {
            ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.toString(),
                    "Page number cannot less than 1");
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public ResponseEntity<?> updateListShopOwnerAccountStatus(ChangeStatusListIdDto changeStatusListIdDto) {
        ShopOwnerStatus shopOwnerStatus = ShopOwnerStatus.getAccountStatus(changeStatusListIdDto.getStatus());
        try {
            int numberStatusChange = shopOwnerRepository.updateListShopOwnerStatus(
                    shopOwnerStatus, changeStatusListIdDto.getIds()
            );
            List<ShopOwner> shopOwners = shopOwnerRepository.findAllById(changeStatusListIdDto.getIds());
            List<Long> listAccountId = shopOwners.stream().map(s -> s.getAccount().getId()).toList();
            if(listAccountId.size() > 0) {
                NotificationDto notificationDto = new NotificationDto();
                notificationDto.setName(NotifiConstant.BAN_SHOP_FOR_USER_NAME);
                notificationDto.setNotiText(shopOwnerStatus.getContentNotification());
                notificationDto.setRole(NotifiConstant.NOTI_USER_ROLE);
                notificationService.pushNotificationForListUserID(listAccountId, notificationDto);
            }
            return ResponseEntity.ok("Update " + numberStatusChange + " shop owner account status successfully.");
        } catch (Exception ex) {
            return ResponseUtils.getErrorResponseBadRequest("Update list shop owner account fail.");
        }
    }

    private ResponseEntity<?> filterShopOwnerAccountByCreatedDateFromTo(
            DateRangeDto dateRange, PageRequest pageRequest
    ) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(DateUtils.timeInMillisecondToDate(dateRange.getDateTo()));
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        Optional<Page<ShopOwner>> shopOwners = shopOwnerRepository.findByCreatedDateBetween(
                DateUtils.timeInMillisecondToDate(dateRange.getDateFrom()),
                calendar.getTime(),
                pageRequest
        );

        if (shopOwners.isPresent()) {
            return getPageNumberWrapperWithShopOwnerAccount(shopOwners.get());
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found shop owner have created date from to.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterShopOwnerAccountByCreatedDateGreaterThanOrEqual(
            DateRangeDto dateRange, PageRequest pageRequest
    ) {
        Optional<Page<ShopOwner>> shopOwners = shopOwnerRepository.findByCreatedDateGreaterThanEqual(
                DateUtils.timeInMillisecondToDate(dateRange.getDateFrom()),
                pageRequest
        );

        if (shopOwners.isPresent()) {
            return getPageNumberWrapperWithShopOwnerAccount(shopOwners.get());
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found shop owner have created date greater than or equal.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterShopOwnerAccountByStatusEqual(
            ShopOwnerAccountFilterDto shopOwnerAccountFilter, PageRequest pageRequest) {
        List<ShopOwnerStatus> shopOwnerStatuses;
        if (Integer.parseInt(shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue()) == 9) {
            shopOwnerStatuses = List.of(ShopOwnerStatus.values());
        } else {
            shopOwnerStatuses = Arrays.asList(
                    ShopOwnerStatus.getAccountStatus(
                            Integer.parseInt(shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue())
                    )
            );
        }

        Optional<Page<ShopOwner>> shopOwners = shopOwnerRepository.findByStatusIn(
                shopOwnerStatuses,
                pageRequest
        );

        if (shopOwners.isPresent()) {
            return getPageNumberWrapperWithShopOwnerAccount(shopOwners.get());
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found this status.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterShopOwnerAccountByAddressContain(
            ShopOwnerAccountFilterDto shopOwnerAccountFilter, PageRequest pageRequest
    ) {
        Optional<Page<ShopOwner>> shopOwners = shopOwnerRepository.findByAddress_AddressLike(
                "%" + shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue() + "%",
                pageRequest
        );

        if (shopOwners.isPresent()) {
            return getPageNumberWrapperWithShopOwnerAccount(shopOwners.get());
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found shop owner have contain this address.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterShopOwnerAccountByShopPhoneContain(
            ShopOwnerAccountFilterDto shopOwnerAccountFilter, PageRequest pageRequest
    ) {
        Optional<Page<ShopOwner>> shopOwners = shopOwnerRepository.findByShopPhoneLike(
                "%" + shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue() + "%",
                pageRequest
        );

        if (shopOwners.isPresent()) {
            return getPageNumberWrapperWithShopOwnerAccount(shopOwners.get());
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found shop owner have contain this shop phone.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterShopOwnerAccountByShopNameContain(
            ShopOwnerAccountFilterDto shopOwnerAccountFilter, PageRequest pageRequest
    ) {
        Optional<Page<ShopOwner>> shopOwners = shopOwnerRepository.findByShopNameLike(
                "%" + shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue() + "%",
                pageRequest
        );

        if (shopOwners.isPresent()) {
            return getPageNumberWrapperWithShopOwnerAccount(shopOwners.get());
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found shop owner have contain this shop name.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterShopOwnerAccountByEmailContain(
            ShopOwnerAccountFilterDto shopOwnerAccountFilter, PageRequest pageRequest
    ) {
        Optional<Page<ShopOwner>> shopOwners = shopOwnerRepository.findByAccount_EmailLike(
                "%" + shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue() + "%",
                pageRequest
        );

        if (shopOwners.isPresent()) {
            return getPageNumberWrapperWithShopOwnerAccount(shopOwners.get());
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found shop owner have contain this email.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterShopOwnerAccountByIdEqual(
            ShopOwnerAccountFilterDto shopOwnerAccountFilter, PageRequest pageRequest
    ) {
        Optional<Page<ShopOwner>> shopOwners = shopOwnerRepository.findById(
                Long.valueOf(shopOwnerAccountFilter.getShopOwnerSearchInfo().getValue()),
                pageRequest
        );

        if (shopOwners.isPresent()) {
            return getPageNumberWrapperWithShopOwnerAccount(shopOwners.get());
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .errorMessage("Not found this shop owner id.")
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<?> filterAllShopOwnerAccountAllFieldEmpty(PageRequest pageRequest) {
        Page<ShopOwner> shopOwners = shopOwnerRepository.findAll(
                pageRequest
        );

        if (!shopOwners.isEmpty()) {
            return getPageNumberWrapperWithShopOwnerAccount(shopOwners);
        } else {
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .errorCode(String.valueOf(HttpStatus.NOT_FOUND.value()))
                    .errorMessage("Not found shop owner account.")
                    .build();
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        }
    }

    private ResponseEntity<?> getPageNumberWrapperWithShopOwnerAccount(Page<ShopOwner> shopOwners) {
        List<ShopOwnerAccountDto> shopOwnerAccounts = shopOwners.stream()
                .map(this::shopOwnerToShopOwnerAccountDto)
                .collect(Collectors.toList());
        PageNumberWrapper<ShopOwnerAccountDto> result = new PageNumberWrapper<>(
                shopOwnerAccounts,
                shopOwners.getTotalPages(),
                shopOwners.getTotalElements()
        );
        return ResponseEntity.ok(result);
    }

    private ShopOwnerAccountDto shopOwnerToShopOwnerAccountDto(ShopOwner shopOwner) {
        return ShopOwnerAccountDto.builder()
                .id(shopOwner.getId())
                .email(shopOwner.getAccount().getEmail())
                .shopName(shopOwner.getShopName())
                .avtUrl(shopOwner.getAvatarImgUrl())
                .shopPhone(shopOwner.getShopPhone())
                .address(shopOwner.getAddress().getAddress())
                .status(shopOwner.getStatus())
                .createdDate(shopOwner.getCreatedDate().getTime())
                .build();
    }

    private PageRequest getPageRequest(
            ShopOwnerAccountFilterDto shopOwnerAccountFilter,
            int pageNumber, Sort.Direction sortDirection
    ) {
        return PageRequest.of(
                pageNumber,
                PagingAndSorting.DEFAULT_PAGE_SHOP_SIZE,
                Sort.by(sortDirection,
                        SortShopOwnerAccountColumn.getColumnByField(shopOwnerAccountFilter.getSortDirection().getField())
                )
        );
    }

    @Async
    protected String uploadImages(MultipartFile multipartImage) throws IOException {
        String originUrl = appProperties.getS3().getUrl();
        String urlImage = "";
        if (multipartImage != null && !multipartImage.isEmpty()) {
            String newFileName = FileNameUtils.getNewImageFileName(multipartImage);
            urlImage = originUrl + newFileName;
            S3Utils.uploadFile(newFileName, multipartImage.getInputStream());
        }
        return urlImage;
    }
}
