package com.example.japantravelplanner.controller;

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
}