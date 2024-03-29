package com.gangoffive.birdtradingplatform.mapper;

import com.gangoffive.birdtradingplatform.dto.CardProductDto;
import com.gangoffive.birdtradingplatform.dto.FoodDto;
import com.gangoffive.birdtradingplatform.entity.Food;
import com.gangoffive.birdtradingplatform.entity.Product;
import com.gangoffive.birdtradingplatform.service.impl.ProductServiceImpl;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring", imports = ProductServiceImpl.class)
public interface FoodMapper {
    @Mapping(target = "id", source = "food.id")
    @Mapping(source = "food.typeFood", target = "type")
    @Mapping(source = "food.shopOwner.id", target = "shopOwner.id")
    @Mapping(source = "food.shopOwner.shopName", target = "shopOwner.shopName")
    @Mapping(source = "food.shopOwner.avatarImgUrl", target = "shopOwner.imgUrl", defaultValue = "https://th.bing.com/th/id/R.aeaa38b7aa3046ce9086cc361c820b4c?rik=uVb%2bxcU7Xy6ZzA&pid=ImgRaw&r=0")
    FoodDto toDto (Food food);

    @InheritInverseConfiguration
    Food toModel (FoodDto foodDto);

    CardProductDto productToCardProduct(Product product);
}
