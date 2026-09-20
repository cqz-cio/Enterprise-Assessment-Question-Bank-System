package com.yf.modules.exam.assignment.importing;

import com.yf.base.utils.CacheKey;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/** Flyway changes menu grants outside the ordinary menu editor's cache eviction. */
@Component @RequiredArgsConstructor
public class CandidateImportCacheRefresh implements ApplicationRunner {
    private final CacheManager caches;
    @Override public void run(ApplicationArguments args) {
        var menu = caches.getCache(CacheKey.MENU);
        if (menu != null) menu.clear();
    }
}
