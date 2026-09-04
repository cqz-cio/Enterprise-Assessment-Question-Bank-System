package com.yf.modules.exam.repo.service;

import com.yf.modules.exam.repo.dto.response.QuestionImportPreviewRespDTO;
import com.yf.modules.exam.repo.dto.response.QuestionImportResultRespDTO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface QuestionImportService {

    QuestionImportPreviewRespDTO validate(String repoId, MultipartFile file);

    QuestionImportResultRespDTO importQuestions(String repoId, MultipartFile file);

    void writeTemplate(HttpServletResponse response) throws IOException;

    void writeErrorReport(String repoId, MultipartFile file, HttpServletResponse response) throws IOException;
}
