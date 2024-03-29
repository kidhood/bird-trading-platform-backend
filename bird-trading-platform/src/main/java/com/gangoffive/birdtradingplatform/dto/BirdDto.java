package com.gangoffive.birdtradingplatform.dto;

import com.gangoffive.birdtradingplatform.entity.Tag;
import com.gangoffive.birdtradingplatform.entity.TypeBird;
import com.gangoffive.birdtradingplatform.enums.Gender;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class BirdDto extends ProductDto{
	private int age;

    private Gender gender;

    private String color;

    private TypeBird type;

    private List<Tag> tags;
}
