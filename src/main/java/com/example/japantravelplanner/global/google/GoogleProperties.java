package com.example.japantravelplanner.global.google;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.google")
public class GoogleProperties {

    private String apiKey;
    private int timeoutSeconds = 10;
}