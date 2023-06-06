package com.gangoffive.birdtradingplatform.service.impl;

import com.gangoffive.birdtradingplatform.dto.AddressDto;
import com.gangoffive.birdtradingplatform.dto.AuthenticationResponseDto;
import com.gangoffive.birdtradingplatform.dto.TokenDto;
import com.gangoffive.birdtradingplatform.dto.UserInfoDto;
import com.gangoffive.birdtradingplatform.entity.Account;
import com.gangoffive.birdtradingplatform.exception.AuthenticateException;
import com.gangoffive.birdtradingplatform.mapper.AddressMapper;
import com.gangoffive.birdtradingplatform.repository.AccountRepository;
import com.gangoffive.birdtradingplatform.security.UserPrincipal;
import com.gangoffive.birdtradingplatform.service.InfoService;
import com.gangoffive.birdtradingplatform.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InfoServiceImpl implements InfoService {
    private final AddressMapper addressMapper;
    private final AccountRepository accountRepository;
    private final JwtService jwtService;
    @Override
    public ResponseEntity<?> getInfo(String email, String token) {
        if (token == null || token.isEmpty()) {
            throw new AuthenticateException("Not correct token to access");
        }
        if (jwtService.extractUsername(token).equalsIgnoreCase(email) && !jwtService.isTokenExpired(token)) {
            Optional<Account> account = accountRepository.findByEmail(email);
            UserPrincipal userPrincipal = UserPrincipal.create(account.get());
            String refreshToken = account.get().getRefreshToken();
            if (refreshToken != null) {
                if (jwtService.isTokenExpired(refreshToken)) {
                    refreshToken = jwtService.generateRefreshToken(userPrincipal);
                    account.get().setRefreshToken(refreshToken);
                    accountRepository.save(account.get());
                }
            } else {
                refreshToken = jwtService.generateRefreshToken(userPrincipal);
                account.get().setRefreshToken(refreshToken);
                accountRepository.save(account.get());
            }

            AddressDto addressDto = addressMapper.toDto(account.get().getAddress());
            TokenDto tokenDto = TokenDto.builder()
                    .accessToken(token)
                    .refreshToken(refreshToken)
                    .build();
            UserInfoDto userInfo = UserInfoDto.builder()
                    .email(account.get().getEmail())
                    .role(account.get().getRole())
                    .fullName(account.get().getFullName())
                    .phoneNumber(account.get().getPhoneNumber())
                    .imgUrl(account.get().getImgUrl())
                    .address(addressDto)
                    .build();
            AuthenticationResponseDto authenticationResponseDto = AuthenticationResponseDto.builder()
                    .token(tokenDto)
                    .userInfo(userInfo)
                    .build();
            return ResponseEntity.ok().body(authenticationResponseDto);
        }
        throw new AuthenticateException("Not correct token to access");
    }
}