package com.gangoffive.birdtradingplatform.controller;

import com.gangoffive.birdtradingplatform.dto.BirdDto;
import com.gangoffive.birdtradingplatform.service.BirdService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class BirdController {
    private final BirdService birdService;

    @GetMapping("birds")
    public List<BirdDto> retrieveAllBird() {
        return birdService.retrieveAllBird();
    }

    @GetMapping("birds/pages/{pageNumber}")
    public List<BirdDto> retrieveAllBirdByPageNumber(@PathVariable int pageNumber) {
        return birdService.retrieveBirdByPageNumber(pageNumber);
    }

    @GetMapping("birds/search")
    public List<BirdDto> findBirdByName(@RequestParam String name) {
        return birdService.findBirdByName(name);
    }

    @PostMapping("/shopowner/birds/update/{id}")
    public void updateBird(@RequestParam BirdDto birdDto) {
        birdService.updateBird(birdDto);
    }

    @DeleteMapping("/shopowner/birds/delete/{id}")
    @PreAuthorize("hasAnyAuthority('shopowner:update')")
    public void deleteBird(@PathVariable("id") Long id) {
        birdService.deleteBirdById(id);
    }
}
