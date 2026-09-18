package com.yf.modules.exam.jobs;

import com.yf.modules.exam.paper.mapper.PaperMapper;
import com.yf.modules.exam.paper.service.PaperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

/** Database-driven recovery covers missed Quartz triggers and application downtime. */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OverduePaperRecovery {
    private final PaperMapper paperMapper;
    private final PaperService paperService;
    private String afterId = "";

    @Scheduled(fixedDelayString = "${exam.recovery.delay-ms:30000}", initialDelayString = "${exam.recovery.initial-delay-ms:10000}")
    public void recover() {
        List<String> ids = paperMapper.selectOverdueIds(afterId);
        for (String id : ids) {
            try {
                // Separate proxied transaction per paper; failures do not roll back the batch.
                paperService.handPaper(id);
            } catch (Exception ex) {
                log.error("Overdue paper recovery failed, paperId={}", id, ex);
            }
            afterId = id;
        }
        // Walk past failing rows so one bad paper cannot starve later batches.
        if (ids.size() < 100) afterId = "";
    }
}
