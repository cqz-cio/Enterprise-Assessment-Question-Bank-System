package com.yf.modules.exam.repo.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yf.base.api.api.ApiRest;
import com.yf.base.api.api.controller.BaseController;
import com.yf.base.api.api.dto.BaseIdReqDTO;
import com.yf.base.api.api.dto.BaseIdsReqDTO;
import com.yf.base.api.api.dto.PagingReqDTO;
import com.yf.modules.exam.repo.dto.RepoQuDTO;
import com.yf.modules.exam.repo.dto.request.RepoQuDetailDTO;
import com.yf.modules.exam.repo.dto.request.RepoQuListReqDTO;
import com.yf.modules.exam.repo.dto.response.QuestionImportPreviewRespDTO;
import com.yf.modules.exam.repo.dto.response.QuestionImportResultRespDTO;
import com.yf.modules.exam.repo.service.QuestionImportService;
import com.yf.modules.exam.repo.service.RepoQuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * <p>
 * 问题题目控制器
 * </p>
 *
 * @author 聪明笨狗
 * @since 2025-04-11 09:42
 */
@Tag(name = "试题")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exam/repo/qu")
public class RepoQuController extends BaseController {

    private final RepoQuService repoQuService;
    private final QuestionImportService questionImportService;

    /**
     * 添加或修改
     *
     * @param reqDTO
     * @return
     */
    @Operation(summary = "添加或修改")
    @RequiresPermissions(value = {"repo:qu:add", "repo:qu:edit"}, logical = Logical.OR)
    @PostMapping("/save")
    public ApiRest<?> save(@RequestBody RepoQuDetailDTO reqDTO) {
        repoQuService.save(reqDTO);
        return super.success();
    }

    /**
     * 批量删除
     *
     * @param reqDTO
     * @return
     */
    @Operation(summary = "批量删除")
    @RequiresPermissions("repo:qu:delete")
    @PostMapping("/delete")
    public ApiRest<?> delete(@RequestBody BaseIdsReqDTO reqDTO) {
        //根据ID删除
        repoQuService.delete(reqDTO.getIds());
        return super.success();
    }

    /**
     * 查找详情
     *
     * @param reqDTO
     * @return
     */
    @Operation(summary = "查找详情")
    @RequiresPermissions("repo:qu:view")
    @PostMapping("/detail")
    public ApiRest<RepoQuDetailDTO> detail(@RequestBody BaseIdReqDTO reqDTO) {
        RepoQuDetailDTO dto = repoQuService.detail(reqDTO.getId());
        return super.success(dto);
    }

    /**
     * 分页查找
     *
     * @param reqDTO
     * @return
     */
    @Operation(summary = "分页查找")
    @RequiresPermissions("repo:qu:view")
    @PostMapping("/paging")
    public ApiRest<IPage<RepoQuDTO>> paging(@RequestBody PagingReqDTO<RepoQuListReqDTO> reqDTO) {

        //分页查询并转换
        IPage<RepoQuDTO> page = repoQuService.paging(reqDTO);

        return super.success(page);
    }

    @Operation(summary = "下载试题 Excel 导入模板")
    @RequiresPermissions("repo:qu:import")
    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        questionImportService.writeTemplate(response);
    }

    @Operation(summary = "校验试题 Excel")
    @RequiresPermissions("repo:qu:import")
    @PostMapping(value = "/import/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiRest<QuestionImportPreviewRespDTO> validateImport(
            @RequestParam("repoId") String repoId,
            @RequestPart("file") MultipartFile file) {
        return super.success(questionImportService.validate(repoId, file));
    }

    @Operation(summary = "确认导入试题 Excel")
    @RequiresPermissions("repo:qu:import")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiRest<QuestionImportResultRespDTO> importQuestions(
            @RequestParam("repoId") String repoId,
            @RequestPart("file") MultipartFile file) {
        return super.success(questionImportService.importQuestions(repoId, file));
    }

    @Operation(summary = "下载试题导入错误报告")
    @RequiresPermissions("repo:qu:import")
    @PostMapping(value = "/import-error-report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void importErrorReport(
            @RequestParam("repoId") String repoId,
            @RequestPart("file") MultipartFile file,
            HttpServletResponse response) throws IOException {
        questionImportService.writeErrorReport(repoId, file, response);
    }
}
