package com.gangoffive.birdtradingplatform.dto;

import com.gangoffive.birdtradingplatform.enums.UserRole;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class UserInfoDto {
    private Long id;
    private String email;
    private UserRole role;
    private String fullName;
    private String shopName;
    private String phoneNumber;
    private String imgUrl;
    private String address;
}
