package com.example.japantravelplanner.restaurant.batch;

import com.example.japantravelplanner.restaurant.entity.Restaurant;
import com.example.japantravelplanner.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 이미 적재된 레스토랑 데이터의 예산 텍스트를 숫자 컬럼으로 채우는 일회성 마이그레이션.
 * 파싱된 값이 하나라도 있으면 이미 실행된 것으로 보고 건너뛴다 (멱등성).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(3)
public class RestaurantBudgetMigrationRunner implements CommandLineRunner {

    private final RestaurantRepository restaurantRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (restaurantRepository.existsByBudgetDinnerMinIsNotNull()) {
            log.info("[BudgetMigration] 이미 예산 파싱이 완료되어 있습니다. 건너뜁니다.");
            return;
        }

        List<Restaurant> restaurants = restaurantRepository.findAll();
        if (restaurants.isEmpty()) {
            log.info("[BudgetMigration] 레스토랑 데이터가 없습니다. 건너뜁니다.");
            return;
        }

        restaurants.forEach(Restaurant::parseBudget);

        // save() 호출 없음: @Transactional 안에서 변경된 Entity는
        // 트랜잭션 커밋 시점에 Dirty Checking으로 자동 UPDATE된다.
        log.info("[BudgetMigration] {}건 예산 파싱 완료 (커밋 시 UPDATE 반영)", restaurants.size());
    }
}