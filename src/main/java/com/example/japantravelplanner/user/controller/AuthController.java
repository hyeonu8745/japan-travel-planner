package com.example.japantravelplanner.user.controller;

import com.example.japantravelplanner.user.dto.LoginRequestDto;
import com.example.japantravelplanner.user.dto.LoginResponseDto;
import com.example.japantravelplanner.user.dto.SignupRequestDto;
import com.example.japantravelplanner.user.dto.SignupResponseDto;
import com.example.japantravelplanner.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponseDto> signup(@Valid @RequestBody SignupRequestDto request) {
        SignupResponseDto response = userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);   // 201
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(userService.login(request));
    }

    // 테스트용: 토큰 인증 확인용 엔드포인트
    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Object principal = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(java.util.Map.of("userId", principal));
    }
}