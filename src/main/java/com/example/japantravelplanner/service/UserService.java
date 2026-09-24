package com.example.japantravelplanner.service;

import com.example.japantravelplanner.dto.SignupRequestDto;
import com.example.japantravelplanner.dto.SignupResponseDto;
import com.example.japantravelplanner.entity.User;
import com.example.japantravelplanner.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
}