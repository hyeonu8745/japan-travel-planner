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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)          // ← 추가: 기본은 읽기 전용
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider; // ⭐ 추가


    @Transactional
    public SignupResponseDto signup(SignupRequestDto request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // 암호화!
                .nickname(request.getNickname())
                .build();

        User savedUser = userRepository.save(user);

        return SignupResponseDto.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .nickname(savedUser.getNickname())
                .build();
    }

    public LoginResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmail(request.getEmail())
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
}