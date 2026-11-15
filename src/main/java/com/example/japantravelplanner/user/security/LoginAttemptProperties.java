package com.example.japantravelplanner.user.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 로그인 시도 제한 (11-15 보안): 같은 이메일 + 같은 IP 로 maxFailures 번 틀리면 lockMinutes 분 막는다 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security.login")
public class LoginAttemptProperties {

    private int maxFailures = 5;
    private int lockMinutes = 15;
}
