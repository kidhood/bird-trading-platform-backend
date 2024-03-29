package com.gangoffive.birdtradingplatform.dto;

import com.gangoffive.birdtradingplatform.enums.UserRole;
import lombok.*;
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class ShopInfoDto {
    private long id;
    private String shopName;
    private String shopPhone;
    private String description;
    private String avatarImgUrl;
    private String coverImgUrl;
    private Long createdDate;
    private AddressDto address;
    private int role;
}
