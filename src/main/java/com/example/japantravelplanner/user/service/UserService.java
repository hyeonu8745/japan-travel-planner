package com.example.japantravelplanner.user.service;

import com.example.japantravelplanner.global.exception.BadRequestException;
import com.example.japantravelplanner.global.exception.ConflictException;
import com.example.japantravelplanner.global.security.JwtTokenProvider;
import com.example.japantravelplanner.user.dto.LoginRequestDto;
import com.example.japantravelplanner.user.dto.LoginResponseDto;
import com.example.japantravelplanner.user.dto.SignupRequestDto;
import com.example.japantravelplanner.user.dto.SignupResponseDto;
import com.example.japantravelplanner.user.entity.User;
import com.example.japantravelplanner.user.repository.UserRepository;
import com.example.japantravelplanner.user.security.LoginAttemptLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptLimiter loginAttempts;


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

    /** 이메일이 없을 때도 비밀번호 확인을 한 번 해서, 응답 시간으로 가입 여부를 알 수 없게 한다 (11-15 보안 2-9) */
    private static final String LOGIN_FAILED = "이메일 또는 비밀번호가 올바르지 않습니다.";

    /** @param clientIp 로그인 시도 제한에 쓰는 요청 주소 (11-15 보안 1-1) */
    public LoginResponseDto login(LoginRequestDto request, String clientIp) {
        String email = normalizeEmail(request.getEmail());
        loginAttempts.checkAllowed(email, clientIp);

        User user = userRepository.findByEmail(email).orElse(null);
        boolean matches = passwordEncoder.matches(request.getPassword(), user == null ? dummyHash() : user.getPassword());
        if (user == null || !matches) {
            loginAttempts.recordFailure(email, clientIp);
            throw new BadRequestException(LOGIN_FAILED);
        }
        loginAttempts.recordSuccess(email, clientIp);

        String token = jwtTokenProvider.createToken(user.getId(), user.getEmail());

        return LoginResponseDto.builder()
                .accessToken(token)
                .userId(user.getId())
                .nickname(user.getNickname())
                .build();
    }

    private volatile String dummyHash;

    private String dummyHash() {
        if (dummyHash == null) dummyHash = passwordEncoder.encode("tabimate-no-such-user");
        return dummyHash;
    }

    /** 앞뒤 공백 제거 + 소문자 변환 (Test@Example.com과 test@example.com을 같은 계정으로) */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}