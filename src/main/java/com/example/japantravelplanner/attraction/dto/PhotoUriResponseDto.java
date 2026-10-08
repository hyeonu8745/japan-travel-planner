package com.example.japantravelplanner.attraction.dto;

/**
 * 사진을 화면에 띄울 주소 (API 키가 들어 있지 않다. <img src>에 그대로 넣는다).
 * 이 주소도 저장하지 말고 볼 때마다 받는다.
 */
public record PhotoUriResponseDto(String photoUri) {
}
