package com.yf.modules.exam.assignment.importing;

import com.yf.base.api.api.ApiRest;
import com.yf.base.api.api.controller.BaseController;
import com.yf.modules.exam.assignment.importing.CandidateImportModels.ImportView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/exam/assignment/candidate")
@RequiresPermissions("exam:assignment:candidate:import")
public class CandidateImportController extends BaseController {
    private final CandidateImportService service;
    public record TaskRequest(@NotBlank String taskId) { }
    @PostMapping("/import-template")
    public ResponseEntity<byte[]> template() { return download(service.template(),"candidate-template.xlsx"); }
    @PostMapping("/import-validate")
    public ApiRest<ImportView> preview(@RequestParam MultipartFile file, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); return success(service.preview(file));
    }
    @PostMapping("/import")
    public ApiRest<ImportView> commit(@Valid @RequestBody TaskRequest req, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); return success(service.commit(req.taskId()));
    }
    @PostMapping("/import-error")
    public ResponseEntity<byte[]> errors(@Valid @RequestBody TaskRequest req) { return download(service.report(req.taskId(),false),"candidate-import-errors.xlsx"); }
    @PostMapping("/import-codes")
    public ResponseEntity<byte[]> codes(@Valid @RequestBody TaskRequest req) { return download(service.report(req.taskId(),true),"candidate-access-codes.xlsx"); }
    @PostMapping("/import-close")
    public ApiRest<?> close(@Valid @RequestBody TaskRequest req) { service.close(req.taskId()); return success(); }
    public record RestoreRequest(String taskId) { }
    @PostMapping("/import-restore")
    public ApiRest<ImportView> restore(@RequestBody(required=false) RestoreRequest req, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); return success(service.restore(req==null?null:req.taskId()));
    }
    private ResponseEntity<byte[]> download(byte[] body,String name) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+name)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(body);
    }
}
