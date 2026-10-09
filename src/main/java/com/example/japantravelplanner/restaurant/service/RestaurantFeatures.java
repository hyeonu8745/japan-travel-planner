package com.example.japantravelplanner.restaurant.service;

import com.example.japantravelplanner.global.google.GooglePlacesClient.Money;
import com.example.japantravelplanner.global.google.GooglePlacesClient.PaymentOptions;
import com.example.japantravelplanner.global.google.GooglePlacesClient.RestaurantPlace;
import com.example.japantravelplanner.restaurant.dto.RestaurantDetailResponseDto.FeaturesDto;

import java.util.ArrayList;
import java.util.List;

/** Google 식당 특성 → 화면용 (true인 것만 한국어 이름으로, 정해진 순서) */
final class RestaurantFeatures {

    private RestaurantFeatures() {
    }

    static FeaturesDto from(RestaurantPlace p) {
        PaymentOptions pay = p.paymentOptions();
        Boolean card = pay == null ? null : pay.acceptsCreditCards();
        Boolean cashOnly = pay == null ? null : pay.acceptsCashOnly();
        Boolean nfc = pay == null ? null : pay.acceptsNfc();
        Integer min = p.priceRange() == null ? null : yen(p.priceRange().startPrice());
        Integer max = p.priceRange() == null ? null : yen(p.priceRange().endPrice());

        List<String> labels = new ArrayList<>();
        add(labels, p.servesBreakfast(), "아침");
        add(labels, p.servesBrunch(), "브런치");
        add(labels, p.servesLunch(), "점심");
        add(labels, p.servesDinner(), "저녁");
        add(labels, p.servesVegetarianFood(), "채식 메뉴");
        add(labels, p.servesCoffee(), "커피");
        add(labels, p.servesDessert(), "디저트");
        add(labels, p.servesBeer(), "맥주");
        add(labels, p.servesWine(), "와인");
        add(labels, p.servesCocktails(), "칵테일");
        add(labels, p.menuForChildren(), "어린이 메뉴");
        add(labels, p.goodForChildren(), "아이 동반");
        add(labels, p.goodForGroups(), "단체");
        add(labels, p.reservable(), "예약 가능");
        add(labels, p.takeout(), "포장");
        add(labels, p.delivery(), "배달");
        add(labels, p.outdoorSeating(), "야외석");
        add(labels, card, "카드 결제");
        add(labels, nfc, "간편 결제");
        add(labels, cashOnly, "현금만");

        return new FeaturesDto(p.servesBreakfast(), p.servesBrunch(), p.servesLunch(), p.servesDinner(),
                p.servesVegetarianFood(), p.servesBeer(), p.servesWine(), p.servesCocktails(), p.servesCoffee(),
                p.servesDessert(), p.menuForChildren(), p.goodForChildren(), p.goodForGroups(), p.reservable(),
                p.dineIn(), p.takeout(), p.delivery(), p.outdoorSeating(), card, cashOnly, nfc, min, max,
                List.copyOf(labels));
    }

    private static void add(List<String> labels, Boolean value, String label) {
        if (Boolean.TRUE.equals(value)) labels.add(label);
    }

    /** 엔화일 때만 정수 엔으로 (다른 통화·잘못된 값이면 null) */
    static Integer yen(Money money) {
        if (money == null || !"JPY".equals(money.currencyCode()) || money.units() == null) return null;
        try {
            return Integer.valueOf(money.units());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
