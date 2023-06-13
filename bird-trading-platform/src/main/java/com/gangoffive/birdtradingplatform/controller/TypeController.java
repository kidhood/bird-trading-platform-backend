package com.gangoffive.birdtradingplatform.controller;

import com.gangoffive.birdtradingplatform.entity.TypeAccessory;
import com.gangoffive.birdtradingplatform.entity.TypeBird;
import com.gangoffive.birdtradingplatform.entity.TypeFood;
import com.gangoffive.birdtradingplatform.service.TypeAccessoryService;
import com.gangoffive.birdtradingplatform.service.TypeBirdService;
import com.gangoffive.birdtradingplatform.service.TypeFoodService;
import com.gangoffive.birdtradingplatform.wrapper.TypeAllWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shop-owner")
@RequiredArgsConstructor
@Slf4j
public class TypeController {
    private final TypeFoodService typeFoodService;
    private final TypeAccessoryService typeAccessoryService;
    private final TypeBirdService typeBirdService;

    @GetMapping("/type-all")
    public ResponseEntity<?> retrieveAllType () {
        List<TypeFood> typeFoods = typeFoodService.getAllTypeFood();
        List<TypeAccessory> typeAccessories = typeAccessoryService.getAllTypeAccessory();
        List<TypeBird> typeBirds = typeBirdService.getAllTypeBird();
        TypeAllWrapper typeAllWrapper = new TypeAllWrapper(typeBirds,typeFoods,typeAccessories);
        return ResponseEntity.ok(typeAllWrapper);
    }

    @GetMapping("/type-birds")
    public ResponseEntity<?> retrieveTypeBird() {
        List<TypeBird> typeBirds = typeBirdService.getAllTypeBird();
        return ResponseEntity.ok(typeBirds);
    }

    @GetMapping("/type-foods")
    public ResponseEntity<?> retrieveTypeFood() {
        List<TypeFood> typeFoods = typeFoodService.getAllTypeFood();
        return ResponseEntity.ok(typeFoods);
    }

    @GetMapping("/type-accessories")
    public ResponseEntity<?> retrieveTypeAccessory() {
        List<TypeAccessory> typeAccessories = typeAccessoryService.getAllTypeAccessory();
        return ResponseEntity.ok(typeAccessories);
    }

}