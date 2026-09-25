package com.example.japantravelplanner.controller;

import com.example.japantravelplanner.dto.LoginRequestDto;
import com.example.japantravelplanner.dto.LoginResponseDto;
import com.example.japantravelplanner.dto.SignupRequestDto;
import com.example.japantravelplanner.dto.SignupResponseDto;
import com.example.japantravelplanner.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponseDto> signup(@RequestBody SignupRequestDto request) {
        SignupResponseDto response = userService.signup(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto request) {
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