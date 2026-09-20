package com.yf.modules.exam.report;

import com.yf.base.api.api.ApiRest;
import com.yf.base.api.api.controller.BaseController;
import com.yf.base.api.api.dto.BaseIdReqDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequiredArgsConstructor @RequestMapping("/api/exam/results")
public class ResultReportController extends BaseController {
    private final ResultReportService service;
    @PostMapping("/paging") @RequiresPermissions("exam:results:view")
    public ApiRest<Map<String,Object>> paging(@Valid @RequestBody ResultQuery q) { return success(service.paging(q)); }
    @PostMapping("/detail") @RequiresPermissions("exam:results:view")
    public ApiRest<Map<String,Object>> detail(@Valid @RequestBody BaseIdReqDTO q) { return success(service.detail(q.getId())); }
    @PostMapping("/options") @RequiresPermissions("exam:results:view")
    public ApiRest<Map<String,Object>> options() { return success(service.options()); }
    @PostMapping("/export-preview") @RequiresPermissions({"exam:results:view","exam:results:export"})
    public ApiRest<Map<String,Object>> preview(@Valid @RequestBody ResultQuery q) { return success(service.preview(q)); }
    @PostMapping("/export") @RequiresPermissions({"exam:results:view","exam:results:export"})
    public ResponseEntity<byte[]> export(@Valid @RequestBody ResultQuery q) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=assessment-results.xlsx")
                .header(HttpHeaders.CACHE_CONTROL,"no-store")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.export(q));
    }
}
