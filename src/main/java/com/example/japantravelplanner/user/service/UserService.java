package com.example.japantravelplanner.user.service;

import com.example.japantravelplanner.user.dto.LoginRequestDto;
import com.example.japantravelplanner.user.dto.LoginResponseDto;
import com.example.japantravelplanner.user.dto.SignupRequestDto;
import com.example.japantravelplanner.user.dto.SignupResponseDto;
import com.example.japantravelplanner.user.entity.User;
import com.example.japantravelplanner.user.repository.UserRepository;
import com.example.japantravelplanner.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.japantravelplanner.global.exception.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)          // ← 추가: 기본은 읽기 전용
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider; // ⭐ 추가


    @Transactional
    public SignupResponseDto signup(SignupRequestDto request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("이미 가입된 이메일입니다.");
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .build();

        User savedUser;
        try {
            // 거의 동시에 같은 이메일로 가입하면 existsByEmail을 둘 다 통과할 수 있음
            // → DB unique 제약에서 걸리는 경우도 409로 응답
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("이미 가입된 이메일입니다.");
        }

        return SignupResponseDto.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .nickname(savedUser.getNickname())
                .build();
    }

    public LoginResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String token = jwtTokenProvider.createToken(user.getId(), user.getEmail());

        return LoginResponseDto.builder()
                .accessToken(token)
                .userId(user.getId())
                .nickname(user.getNickname())
                .build();
    }

    /** 앞뒤 공백 제거 + 소문자 변환 (Test@Example.com과 test@example.com을 같은 계정으로) */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}