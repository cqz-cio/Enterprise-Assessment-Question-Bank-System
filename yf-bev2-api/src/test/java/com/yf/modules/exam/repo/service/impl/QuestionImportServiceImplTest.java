package com.yf.modules.exam.repo.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yf.ability.redis.service.RedisService;
import com.yf.modules.exam.repo.dto.request.RepoQuDetailDTO;
import com.yf.modules.exam.repo.dto.response.QuestionImportPreviewRespDTO;
import com.yf.modules.exam.repo.dto.response.QuestionImportResultRespDTO;
import com.yf.modules.exam.repo.entity.Repo;
import com.yf.modules.exam.repo.entity.RepoQu;
import com.yf.modules.exam.repo.service.RepoQuService;
import com.yf.modules.exam.repo.service.RepoService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionImportServiceImplTest {

    @Mock
    private RepoService repoService;
    @Mock
    private RepoQuService repoQuService;
    @Mock
    private RedisService redisService;
    @Mock
    private QuestionImportWorkbookService workbookService;

    private QuestionImportServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "question-import-test"),
                RepoQu.class);
        service = new QuestionImportServiceImpl(repoService, repoQuService, redisService, workbookService);
        Repo repo = new Repo();
        repo.setId("repo-1");
        repo.setStatus(1);
        org.mockito.Mockito.lenient().when(repoService.getById("repo-1")).thenReturn(repo);
    }

    @Test
    void validateSupportsAllTypesAndReportsDuplicateAndInvalidRows() throws Exception {
        RepoQu existing = new RepoQu();
        existing.setQuType("radio");
        existing.setContent("<p>中国的首都是哪里？</p>");
        when(repoQuService.list(any(Wrapper.class))).thenReturn(List.of(existing));

        MockMultipartFile file = workbook(
                row("Q001", "单选题", "中国的首都是哪里？", "北京", "上海", "", "", "", "", "A",
                        "简单", "", "", "常识"),
                row("Q002", "多选题", "以下哪些属于关系型数据库？", "MySQL", "Redis", "PostgreSQL", "", "", "",
                        "A,C", "一般", "MySQL 与 PostgreSQL 属于关系型数据库", "", "数据库"),
                row("Q003", "判断题", "Java 是面向对象语言。", "", "", "", "", "", "", "正确",
                        "简单", "", "", "Java"),
                row("Q004", "简答题", "请说明事务的 ACID 特性。", "", "", "", "", "", "", "",
                        "较难", "原子性、一致性、隔离性、持久性", "每项 2.5 分", "数据库,事务"),
                row("Q005", "多选题", "无效多选题", "A选项", "B选项", "", "", "", "", "A",
                        "一般", "", "", "")
        );

        QuestionImportPreviewRespDTO preview = service.validate("repo-1", file);

        assertEquals(5, preview.getTotalCount());
        assertEquals(3, preview.getValidCount());
        assertEquals(1, preview.getDuplicateCount());
        assertEquals(1, preview.getFailureCount());
        assertTrue(preview.getIssues().stream().anyMatch(issue -> "DUPLICATE".equals(issue.getIssueType())));
        assertTrue(preview.getIssues().stream().anyMatch(issue -> issue.getMessage().contains("至少需要两个")));
    }

    @Test
    void importPersistsValidRowsAsEnabledAndKeepsShortAnswerFields() throws Exception {
        when(repoQuService.list(any(Wrapper.class))).thenReturn(List.of());
        when(redisService.tryLock("repo:qu:import:repo-1", 60_000L, 1, 100L)).thenReturn(true);
        MockMultipartFile file = workbook(
                row("Q101", "单选题", "2 + 2 等于多少？", "3", "4", "5", "", "", "", "B",
                        "简单", "基础计算", "", "数学"),
                row("Q102", "简答题", "简述需求评审的目的。", "", "", "", "", "", "", "",
                        "一般", "确保范围一致并提前发现风险", "覆盖范围、风险和验收标准", "需求")
        );

        QuestionImportResultRespDTO result = service.importQuestions("repo-1", file);

        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getDuplicateCount());
        assertEquals(0, result.getFailureCount());

        ArgumentCaptor<RepoQuDetailDTO> captor = ArgumentCaptor.forClass(RepoQuDetailDTO.class);
        verify(repoQuService, org.mockito.Mockito.times(2)).save(captor.capture());
        RepoQuDetailDTO choice = captor.getAllValues().get(0);
        RepoQuDetailDTO shortAnswer = captor.getAllValues().get(1);

        assertEquals(1, choice.getStatus());
        assertEquals(3, choice.getAnswerList().size());
        assertEquals("short", shortAnswer.getQuType());
        assertEquals(1, shortAnswer.getStatus());
        assertTrue(shortAnswer.getAnswerList().isEmpty());
        assertEquals("<p>确保范围一致并提前发现风险</p>", shortAnswer.getReferenceAnswer());
        assertEquals("<p>覆盖范围、风险和验收标准</p>", shortAnswer.getGradingCriteria());
        assertEquals("需求", shortAnswer.getTags());
        assertNull(shortAnswer.getAnalysis());
        verify(redisService).unlock("repo:qu:import:repo-1");
    }

    @Test
    void generatedTemplateContainsGuideDataExamplesAndDictionary() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new QuestionImportWorkbookService().writeTemplate(response);

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertEquals(4, workbook.getNumberOfSheets());
            assertEquals("填写说明", workbook.getSheetName(0));
            assertEquals("试题数据", workbook.getSheetName(1));
            assertEquals("填写示例", workbook.getSheetName(2));
            assertEquals("字段字典", workbook.getSheetName(3));
            Sheet dataSheet = workbook.getSheet("试题数据");
            assertEquals(0, dataSheet.getLastRowNum());
            assertEquals(QuestionImportServiceImpl.HEADERS.size(),
                    dataSheet.getRow(0).getLastCellNum());
            assertEquals("题型*", dataSheet.getRow(0).getCell(1).getStringCellValue());
        }
        assertTrue(response.getHeader("Content-Disposition").contains("filename*=UTF-8''"));
    }

    private MockMultipartFile workbook(String[]... values) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(QuestionImportServiceImpl.DATA_SHEET_NAME);
            Row header = sheet.createRow(0);
            for (int index = 0; index < QuestionImportServiceImpl.HEADERS.size(); index++) {
                header.createCell(index).setCellValue(QuestionImportServiceImpl.HEADERS.get(index));
            }
            for (int rowIndex = 0; rowIndex < values.length; rowIndex++) {
                Row excelRow = sheet.createRow(rowIndex + 1);
                for (int column = 0; column < values[rowIndex].length; column++) {
                    excelRow.createCell(column).setCellValue(values[rowIndex][column]);
                }
            }
            workbook.write(output);
            return new MockMultipartFile("file", "questions.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }

    private String[] row(String... values) {
        return values;
    }
}
