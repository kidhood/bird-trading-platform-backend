package com.gangoffive.birdtradingplatform.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum FieldOrderDetailTable {
    ID("id"),
    ORDER_ID("orderId"),
    PRODUCT_ID("productId"),
    PRODUCT_NAME("name"),
    PRICE("price"),
    PROMOTION_RATE("promotionRate"),
    CREATED_DATE("createdDate"),
    REVIEW_RATING("reviewRating");

    private String field;
}
