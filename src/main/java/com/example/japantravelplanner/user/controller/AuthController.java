package com.example.japantravelplanner.user.controller;

import com.example.japantravelplanner.global.exception.UnauthorizedException;
import com.example.japantravelplanner.user.dto.LoginRequestDto;
import com.example.japantravelplanner.user.dto.LoginResponseDto;
import com.example.japantravelplanner.user.dto.SignupRequestDto;
import com.example.japantravelplanner.user.dto.SignupResponseDto;
import com.example.japantravelplanner.user.service.UserService;
import com.example.japantravelplanner.user.token.RefreshCookie;
import com.example.japantravelplanner.user.token.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CookieValue;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final RefreshTokenService refreshTokens;
    private final RefreshCookie refreshCookie;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponseDto> signup(@Valid @RequestBody SignupRequestDto request) {
        SignupResponseDto response = userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);   // 201
    }

    /**
     * 로그인: 본문은 예전과 같다(접근 토큰 1시간). 11-15 갱신 토큰(7일)을 HttpOnly 쿠키로 같이 준다.
     * 같은 브라우저의 예전 갱신 토큰 쿠키가 오면 그 로그인은 닫고 새로 연다 (브라우저 수를 다시 세지 않게)
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request,
                                                  @CookieValue(name = RefreshCookie.NAME, required = false) String previous,
                                                  HttpServletRequest http) {
        LoginResponseDto body = userService.login(request, http.getRemoteAddr());
        String refresh = refreshTokens.startSession(body.getUserId(), previous);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie.issue(refresh).toString()).body(body);
    }

    /** 접근 토큰이 만료됐을 때: 쿠키의 갱신 토큰으로 새 접근 토큰 + 새 갱신 토큰(쿠키). 안 되면 401 → 다시 로그인 */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = RefreshCookie.NAME, required = false) String token) {
        RefreshTokenService.Rotation rotation;
        try {
            rotation = refreshTokens.refresh(token);
        } catch (UnauthorizedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
                    .body(java.util.Map.of("message", e.getMessage()));
        }
        LoginResponseDto body = userService.issueAccess(rotation.userId());
        ResponseEntity.BodyBuilder res = ResponseEntity.ok();
        if (rotation.newToken() != null) {
            res.header(HttpHeaders.SET_COOKIE, refreshCookie.issue(rotation.newToken()).toString());
        }
        return res.body(body);
    }

    /** 로그아웃: 이 브라우저의 갱신 토큰을 폐기하고 쿠키를 지운다. 접근 토큰은 프론트가 버린다 (최대 1시간 남음) */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = RefreshCookie.NAME, required = false) String token) {
        refreshTokens.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString()).build();
    }

    // 테스트용: 토큰 인증 확인용 엔드포인트
    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Object principal = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(java.util.Map.of("userId", principal));
    }
}