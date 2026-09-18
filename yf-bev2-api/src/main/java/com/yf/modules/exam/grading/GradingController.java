package com.yf.modules.exam.grading;

import com.yf.base.api.api.ApiRest;
import com.yf.base.api.api.controller.BaseController;
import com.yf.base.api.api.dto.BaseIdReqDTO;
import com.yf.modules.exam.grading.dto.*;
import com.yf.system.modules.user.UserUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Tag(name="人工阅卷")
@RestController @RequiredArgsConstructor @RequestMapping("/api/exam/grading")
public class GradingController extends BaseController {
    private final GradingService service;

    @Operation(summary="阅卷岗位筛选选项") @PostMapping("/positions") @RequiresPermissions("exam:grading:view")
    public ApiRest<java.util.List<Map<String,Object>>> positions() { return success(service.positions()); }

    @Operation(summary="阅卷列表") @PostMapping("/paging") @RequiresPermissions("exam:grading:view")
    public ApiRest<Map<String,Object>> paging(@Valid @RequestBody GradingQueryDTO request) {
        return success(service.paging(request));
    }
    @Operation(summary="阅卷详情及评分记录") @PostMapping("/detail") @RequiresPermissions("exam:grading:view")
    public ApiRest<Map<String,Object>> detail(@Valid @RequestBody BaseIdReqDTO request) {
        return success(service.detail(request.getId()));
    }
    @Operation(summary="保存单题评分") @PostMapping("/question/save")
    @RequiresPermissions({"exam:grading:view","exam:grading:score"})
    public ApiRest<Map<String,Object>> save(@Valid @RequestBody GradeSaveDTO request) {
        return success(service.save(request,UserUtils.getUserId()));
    }
    @Operation(summary="完成阅卷并生成最终结果") @PostMapping("/finalize")
    @RequiresPermissions({"exam:grading:view","exam:grading:finalize"})
    public ApiRest<Map<String,Object>> finalizePaper(@Valid @RequestBody GradeFinalizeDTO request) {
        return success(service.finalizePaper(request,UserUtils.getUserId()));
    }
}
