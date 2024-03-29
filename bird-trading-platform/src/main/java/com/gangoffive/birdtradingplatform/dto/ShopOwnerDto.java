package com.gangoffive.birdtradingplatform.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class ShopOwnerDto {
    private Long id;
    private String shopName;
    private String imgUrl;
    private AddressDto address;
}
