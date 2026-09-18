package com.yf.config;

import com.yf.base.utils.CacheKey;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/** Flyway may add menus without invoking the normal menu-service cache eviction. */
@Component
@RequiredArgsConstructor
public class MenuCacheStartup implements ApplicationRunner {
    private final CacheManager cacheManager;

    @Override
    public void run(ApplicationArguments args) {
        var cache = cacheManager.getCache(CacheKey.MENU);
        if (cache != null) cache.clear();
    }
}
