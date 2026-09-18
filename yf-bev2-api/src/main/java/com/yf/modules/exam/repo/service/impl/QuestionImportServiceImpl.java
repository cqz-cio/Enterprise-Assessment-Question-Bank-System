package com.yf.modules.exam.repo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yf.ability.redis.service.RedisService;
import com.yf.base.api.exception.ServiceException;
import com.yf.modules.exam.repo.dto.RepoQuAnswerDTO;
import com.yf.modules.exam.repo.dto.request.RepoQuDetailDTO;
import com.yf.modules.exam.repo.dto.response.QuestionImportIssueDTO;
import com.yf.modules.exam.repo.dto.response.WordQuestionPreviewDTO;
import java.util.function.Supplier;
import com.yf.modules.exam.repo.dto.response.QuestionImportPreviewRespDTO;
import com.yf.modules.exam.repo.dto.response.QuestionImportResultRespDTO;
import com.yf.modules.exam.repo.entity.Repo;
import com.yf.modules.exam.repo.entity.RepoQu;
import com.yf.modules.exam.repo.service.QuestionImportService;
import com.yf.modules.exam.repo.service.RepoQuService;
import com.yf.modules.exam.repo.service.RepoService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionImportServiceImpl implements QuestionImportService {

    static final String TEMPLATE_VERSION = "QUESTION_IMPORT_V1";
    static final String DATA_SHEET_NAME = "试题数据";
    static final int MAX_DATA_ROWS = 1000;
    static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    static final List<String> HEADERS = List.of(
            "题目编号", "题型*", "题干*", "选项A", "选项B", "选项C", "选项D",
            "选项E", "选项F", "正确答案", "难度*", "解析/参考答案", "评分要点", "标签"
    );
    private static final List<String> OPTION_LABELS = List.of("A", "B", "C", "D", "E", "F");
    private static final Map<String, String> QUESTION_TYPES = Map.ofEntries(
            Map.entry("单选题", "radio"), Map.entry("radio", "radio"),
            Map.entry("多选题", "multi"), Map.entry("multi", "multi"),
            Map.entry("判断题", "judge"), Map.entry("judge", "judge"),
            Map.entry("简答题", "short"), Map.entry("short", "short")
    );
    private static final Map<String, String> DIFFICULTIES = Map.ofEntries(
            Map.entry("简单", "easy"), Map.entry("easy", "easy"),
            Map.entry("一般", "normal"), Map.entry("中等", "normal"), Map.entry("normal", "normal"),
            Map.entry("较难", "hard"), Map.entry("困难", "hard"), Map.entry("hard", "hard"),
            Map.entry("极难", "extreme"), Map.entry("extreme", "extreme")
    );

    private final RepoService repoService;
    private final RepoQuService repoQuService;
    private final RedisService redisService;
    private final QuestionImportWorkbookService workbookService;
    private final WordQuestionParser wordParser = new WordQuestionParser();

    @Override
    public QuestionImportPreviewRespDTO validate(String repoId, MultipartFile file) {
        return parseAndValidate(repoId, file).toPreview();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionImportResultRespDTO importQuestions(String repoId, MultipartFile file) {
        return persistValidated(repoId, () -> parseAndValidate(repoId, file));
    }

    @Override
    public QuestionImportPreviewRespDTO validateWord(String repoId, MultipartFile file, String defaultDifficulty) {
        return parseAndValidateWord(repoId, file, defaultDifficulty).toPreview();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionImportResultRespDTO importWord(String repoId, MultipartFile file, String defaultDifficulty) {
        return persistValidated(repoId, () -> parseAndValidateWord(repoId, file, defaultDifficulty));
    }

    @Override
    public void writeWordTemplate(HttpServletResponse response) throws IOException {
        new WordQuestionTemplateWriter().write(response);
    }

    @Override
    public void writeWordErrorReport(String repoId, MultipartFile file, String defaultDifficulty,
                                     HttpServletResponse response) throws IOException {
        ValidationBundle bundle = parseAndValidateWord(repoId, file, defaultDifficulty);
        if (bundle.issues.isEmpty()) throw new ServiceException("当前文件没有问题题目，无需下载报告");
        workbookService.writeErrorReport(response, bundle.toErrorReportRows());
    }

    private ValidationBundle parseAndValidateWord(String repoId, MultipartFile file, String defaultDifficulty) {
        validateTargetRepo(repoId);
        if (file == null || file.isEmpty()) throw new ServiceException("请选择需要导入的 Word 文件");
        if (file.getSize() > MAX_FILE_SIZE) throw new ServiceException("Word 文件不能超过 10 MB");
        if (!StringUtils.defaultString(file.getOriginalFilename()).toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new ServiceException("仅支持 .docx 格式，不支持旧版 .doc 文件");
        }
        if (StringUtils.isNotBlank(defaultDifficulty) && !Set.of("简单", "一般", "较难", "极难").contains(defaultDifficulty)) {
            throw new ServiceException("统一难度仅支持简单、一般、较难和极难");
        }
        ValidationBundle bundle = new ValidationBundle();
        bundle.templateVersion = "WORD_QUESTION_IMPORT_V1";
        Set<String> duplicateKeys = loadExistingDuplicateKeys(repoId);
        try (InputStream input = file.getInputStream()) {
            for (WordQuestionParser.ParsedQuestion parsed : wordParser.parse(input, defaultDifficulty)) {
                QuestionRow row = new QuestionRow(parsed.paragraph(), parsed.values());
                bundle.totalCount++;
                bundle.rowsByNumber.put(row.rowNumber, row);
                for (WordQuestionParser.Issue issue : parsed.issues()) {
                    bundle.addIssue(row, issue.field(), issue.message(), "ERROR");
                }
                if (StringUtils.isBlank(row.difficultyLabel)) {
                    bundle.addIssue(row, "难度", "未填写难度，请返回上传步骤统一设置或修改 Word", "ERROR");
                }
                acceptValidatedRow(row, bundle, duplicateKeys);
            }
        } catch (IOException e) {
            throw new ServiceException("Word 读取失败，请重新上传");
        }
        return bundle;
    }

    private QuestionImportResultRespDTO persistValidated(String repoId, Supplier<ValidationBundle> validate) {
        String lockKey = "repo:qu:import:" + repoId;
        if (!redisService.tryLock(lockKey, 60_000L, 1, 100L)) {
            throw new ServiceException("当前题库正在执行导入，请稍后重试");
        }
        try {
            ValidationBundle bundle = validate.get();
            String errorReport = null;
            if (!bundle.issues.isEmpty()) {
                try {
                    errorReport = Base64.getEncoder().encodeToString(
                            workbookService.createErrorReport(bundle.toErrorReportRows()));
                } catch (IOException ex) {
                    throw new ServiceException("错误报告生成失败，本次未导入，请重试");
                }
            }
            for (QuestionRow row : bundle.validRows) {
                repoQuService.save(toQuestion(row, repoId));
            }
            return QuestionImportResultRespDTO.builder()
                    .totalCount(bundle.totalCount)
                    .successCount(bundle.validRows.size())
                    .duplicateCount(bundle.duplicateRows.size())
                    .failureCount(bundle.failedRows.size())
                    .templateVersion(bundle.templateVersion)
                    .issues(bundle.issues)
                    .errorReportBase64(errorReport)
                    .build();
        } finally {
            redisService.unlock(lockKey);
        }
    }

    @Override
    public void writeTemplate(HttpServletResponse response) throws IOException {
        workbookService.writeTemplate(response);
    }

    @Override
    public void writeErrorReport(String repoId, MultipartFile file, HttpServletResponse response) throws IOException {
        ValidationBundle bundle = parseAndValidate(repoId, file);
        if (bundle.issues.isEmpty()) {
            throw new ServiceException("当前文件没有错误行或重复题，无需下载错误报告");
        }
        workbookService.writeErrorReport(response, bundle.toErrorReportRows());
    }

    private ValidationBundle parseAndValidate(String repoId, MultipartFile file) {
        validateTargetRepo(repoId);
        validateFile(file);
        ValidationBundle bundle = new ValidationBundle();
        Set<String> duplicateKeys = loadExistingDuplicateKeys(repoId);
        DataFormatter formatter = new DataFormatter(Locale.CHINA);

        try (InputStream input = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = workbook.getSheet(DATA_SHEET_NAME);
            if (sheet == null) {
                throw new ServiceException("Excel 缺少“试题数据”工作表，请重新下载标准模板");
            }
            if (sheet.getNumMergedRegions() > 0) {
                throw new ServiceException("“试题数据”工作表不允许存在合并单元格");
            }
            validateHeader(sheet.getRow(0), formatter);

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row excelRow = sheet.getRow(rowIndex);
                if (excelRow == null || isBlankRow(excelRow, formatter)) {
                    continue;
                }
                bundle.totalCount++;
                if (bundle.totalCount > MAX_DATA_ROWS) {
                    throw new ServiceException("单次最多导入 " + MAX_DATA_ROWS + " 道题，请拆分文件后重试");
                }
                QuestionRow row = readRow(excelRow, formatter);
                bundle.rowsByNumber.put(row.rowNumber, row);
                validateUnsupportedCells(excelRow, row, bundle);
                acceptValidatedRow(row, bundle, duplicateKeys);
            }
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("Excel 文件读取失败，请确认文件未加密、未损坏且使用标准模板");
        }
        if (bundle.totalCount == 0) {
            throw new ServiceException("“试题数据”工作表没有题目，请填写后再上传");
        }
        return bundle;
    }

    private void acceptValidatedRow(QuestionRow row, ValidationBundle bundle, Set<String> duplicateKeys) {
        validateQuestionRow(row, bundle);
        if (bundle.failedRows.contains(row.rowNumber)) return;
        String key = row.questionType + "|" + normalizeQuestionText(row.content);
        if (!duplicateKeys.add(key)) {
            bundle.addIssue(row, "题干", "同一题库或文件中已存在相同题型和题干，已跳过", "DUPLICATE");
        } else {
            bundle.validRows.add(row);
        }
    }

    private void validateTargetRepo(String repoId) {
        if (StringUtils.isBlank(repoId)) {
            throw new ServiceException("请先选择目标题库");
        }
        Repo repo = repoService.getById(repoId);
        if (repo == null) {
            throw new ServiceException("目标题库不存在");
        }
        if (repo.getStatus() != null && repo.getStatus() == 0) {
            throw new ServiceException("目标题库已停用，不能导入试题");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择需要导入的 Excel 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ServiceException("Excel 文件不能超过 10 MB");
        }
        String fileName = StringUtils.defaultString(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".xlsx")) {
            throw new ServiceException("仅支持 .xlsx 格式的标准模板");
        }
    }

    private void validateHeader(Row headerRow, DataFormatter formatter) {
        if (headerRow == null) {
            throw new ServiceException("“试题数据”工作表缺少表头");
        }
        for (int index = 0; index < HEADERS.size(); index++) {
            String actual = cellText(headerRow.getCell(index), formatter);
            if (!HEADERS.get(index).equals(actual)) {
                throw new ServiceException("第 " + (index + 1) + " 列表头应为“" + HEADERS.get(index) + "”，请勿修改标准模板表头");
            }
        }
        for (int index = HEADERS.size(); index < headerRow.getLastCellNum(); index++) {
            if (StringUtils.isNotBlank(cellText(headerRow.getCell(index), formatter))) {
                throw new ServiceException("标准模板不允许增加自定义列");
            }
        }
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (int index = 0; index < HEADERS.size(); index++) {
            if (StringUtils.isNotBlank(cellText(row.getCell(index), formatter))) {
                return false;
            }
        }
        return true;
    }

    private QuestionRow readRow(Row row, DataFormatter formatter) {
        List<String> values = new ArrayList<>(HEADERS.size());
        for (int index = 0; index < HEADERS.size(); index++) {
            values.add(cleanCellText(cellText(row.getCell(index), formatter)));
        }
        return new QuestionRow(row.getRowNum() + 1, values);
    }

    private String cellText(Cell cell, DataFormatter formatter) {
        return cell == null ? "" : formatter.formatCellValue(cell);
    }

    private String cleanCellText(String value) {
        return StringUtils.defaultString(value).replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private void validateUnsupportedCells(Row excelRow, QuestionRow row, ValidationBundle bundle) {
        for (int index = 0; index < HEADERS.size(); index++) {
            Cell cell = excelRow.getCell(index);
            if (cell != null && cell.getCellType() == CellType.FORMULA) {
                bundle.addIssue(row, cleanHeader(HEADERS.get(index)), "不支持公式单元格，请粘贴为纯文本", "ERROR");
            } else if (cell != null && cell.getCellType() == CellType.ERROR) {
                bundle.addIssue(row, cleanHeader(HEADERS.get(index)), "单元格包含 Excel 错误值", "ERROR");
            }
        }
    }

    private void validateQuestionRow(QuestionRow row, ValidationBundle bundle) {
        validateLength(row, row.externalCode, 64, "题目编号", bundle);
        validateLength(row, row.content, 20000, "题干", bundle);
        validateLength(row, row.explanation, 20000, "解析/参考答案", bundle);
        validateLength(row, row.gradingCriteria, 20000, "评分要点", bundle);
        validateLength(row, row.tags, 500, "标签", bundle);
        if (StringUtils.isBlank(row.content)) {
            bundle.addIssue(row, "题干", "题干不能为空", "ERROR");
        }
        row.questionType = QUESTION_TYPES.get(normalizeEnum(row.questionTypeLabel));
        if (row.questionType == null) {
            bundle.addIssue(row, "题型", "仅支持单选题、多选题、判断题和简答题", "ERROR");
        }
        row.difficulty = DIFFICULTIES.get(normalizeEnum(row.difficultyLabel));
        if (row.difficulty == null) {
            bundle.addIssue(row, "难度", "仅支持简单、一般、较难和极难", "ERROR");
        }
        for (int index = 0; index < row.options.size(); index++) {
            validateLength(row, row.options.get(index), 5000, "选项" + OPTION_LABELS.get(index), bundle);
        }
        if (row.questionType == null) {
            return;
        }
        switch (row.questionType) {
            case "radio" -> validateChoiceQuestion(row, bundle, false);
            case "multi" -> validateChoiceQuestion(row, bundle, true);
            case "judge" -> validateJudgeQuestion(row, bundle);
            case "short" -> validateShortQuestion(row, bundle);
            default -> bundle.addIssue(row, "题型", "不支持的题型", "ERROR");
        }
    }

    private void validateChoiceQuestion(QuestionRow row, ValidationBundle bundle, boolean multi) {
        if (row.options.stream().filter(StringUtils::isNotBlank).count() < 2) {
            bundle.addIssue(row, "选项", "选择题至少需要填写两个选项", "ERROR");
        }
        boolean blankSeen = false;
        Set<String> optionContents = new HashSet<>();
        for (int index = 0; index < row.options.size(); index++) {
            String option = row.options.get(index);
            if (StringUtils.isBlank(option)) {
                blankSeen = true;
                continue;
            }
            if (blankSeen) {
                bundle.addIssue(row, "选项" + OPTION_LABELS.get(index), "选项必须从 A 开始连续填写，中间不能留空", "ERROR");
            }
            if (!optionContents.add(normalizeQuestionText(option))) {
                bundle.addIssue(row, "选项" + OPTION_LABELS.get(index), "选项内容不能重复", "ERROR");
            }
        }
        Set<String> answers = parseChoiceAnswers(row.correctAnswer);
        if (answers == null) {
            bundle.addIssue(row, "正确答案", "答案应填写 A-F，多个答案使用英文逗号分隔，例如 A,C", "ERROR");
            return;
        }
        if (multi && answers.size() < 2) {
            bundle.addIssue(row, "正确答案", "多选题至少需要两个正确答案", "ERROR");
        }
        if (!multi && answers.size() != 1) {
            bundle.addIssue(row, "正确答案", "单选题只能填写一个正确答案", "ERROR");
        }
        for (String answer : answers) {
            int optionIndex = OPTION_LABELS.indexOf(answer);
            if (optionIndex < 0 || StringUtils.isBlank(row.options.get(optionIndex))) {
                bundle.addIssue(row, "正确答案", "答案 " + answer + " 没有对应的有效选项", "ERROR");
            }
        }
        row.answerLabels = answers;
        rejectObjectiveCriteria(row, bundle);
    }

    private void validateJudgeQuestion(QuestionRow row, ValidationBundle bundle) {
        if (row.options.stream().anyMatch(StringUtils::isNotBlank)) {
            bundle.addIssue(row, "选项", "判断题不需要填写选项", "ERROR");
        }
        if (!"正确".equals(row.correctAnswer) && !"错误".equals(row.correctAnswer)) {
            bundle.addIssue(row, "正确答案", "判断题答案只能填写“正确”或“错误”", "ERROR");
        }
        rejectObjectiveCriteria(row, bundle);
    }

    private void rejectObjectiveCriteria(QuestionRow row, ValidationBundle bundle) {
        if (StringUtils.isNotBlank(row.gradingCriteria)) {
            bundle.addIssue(row, "评分要点", "评分要点仅用于简答题", "ERROR");
        }
    }

    private void validateShortQuestion(QuestionRow row, ValidationBundle bundle) {
        if (row.options.stream().anyMatch(StringUtils::isNotBlank)) {
            bundle.addIssue(row, "选项", "简答题不需要填写选项", "ERROR");
        }
        if (StringUtils.isNotBlank(row.correctAnswer)) {
            bundle.addIssue(row, "正确答案", "简答题正确答案列应留空，请填写参考答案或评分要点", "ERROR");
        }
    }

    private void validateLength(QuestionRow row, String value, int max, String field, ValidationBundle bundle) {
        if (value != null && value.length() > max) {
            bundle.addIssue(row, field, field + "不能超过 " + max + " 个字符", "ERROR");
        }
    }

    private Set<String> parseChoiceAnswers(String value) {
        String normalized = StringUtils.defaultString(value).toUpperCase(Locale.ROOT)
                .replace('，', ',').replaceAll("\\s+", "");
        if (!normalized.matches("[A-F](,[A-F]){0,5}")) {
            return null;
        }
        List<String> parts = Arrays.asList(normalized.split(","));
        Set<String> answers = new LinkedHashSet<>(parts);
        return answers.size() == parts.size() ? answers : null;
    }

    private Set<String> loadExistingDuplicateKeys(String repoId) {
        LambdaQueryWrapper<RepoQu> query = new LambdaQueryWrapper<RepoQu>()
                .eq(RepoQu::getRepoId, repoId).select(RepoQu::getQuType, RepoQu::getContent);
        return repoQuService.list(query).stream()
                .filter(item -> StringUtils.isNotBlank(item.getQuType()) && StringUtils.isNotBlank(item.getContent()))
                .map(item -> item.getQuType() + "|" + normalizeQuestionText(item.getContent()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String normalizeQuestionText(String value) {
        String plain = StringUtils.defaultString(value)
                .replaceAll("(?is)<script[^>]*>.*?</script>", "")
                .replaceAll("(?is)<style[^>]*>.*?</style>", "")
                .replaceAll("(?is)<[^>]+>", "");
        return HtmlUtils.htmlUnescape(plain).replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEnum(String value) {
        return StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
    }

    private RepoQuDetailDTO toQuestion(QuestionRow row, String repoId) {
        RepoQuDetailDTO dto = new RepoQuDetailDTO();
        dto.setRepoId(repoId);
        dto.setExternalCode(StringUtils.trimToNull(row.externalCode));
        dto.setQuType(row.questionType);
        dto.setDifficultyLevel(row.difficulty);
        dto.setContent(toSafeHtml(row.content));
        dto.setStatus(1);
        dto.setTags(normalizeTags(row.tags));
        if ("short".equals(row.questionType)) {
            dto.setReferenceAnswer(toSafeHtml(row.explanation));
            dto.setGradingCriteria(toSafeHtml(row.gradingCriteria));
            dto.setAnswerList(List.of());
        } else {
            dto.setAnalysis(toSafeHtml(row.explanation));
            dto.setAnswerList(toAnswers(row));
        }
        return dto;
    }

    private List<RepoQuAnswerDTO> toAnswers(QuestionRow row) {
        if ("judge".equals(row.questionType)) {
            return List.of(answer("A", "正确", "正确".equals(row.correctAnswer)),
                    answer("B", "错误", "错误".equals(row.correctAnswer)));
        }
        List<RepoQuAnswerDTO> answers = new ArrayList<>();
        for (int index = 0; index < row.options.size(); index++) {
            if (StringUtils.isNotBlank(row.options.get(index))) {
                String label = OPTION_LABELS.get(index);
                answers.add(answer(label, row.options.get(index), row.answerLabels.contains(label)));
            }
        }
        return answers;
    }

    private RepoQuAnswerDTO answer(String tag, String content, boolean right) {
        RepoQuAnswerDTO dto = new RepoQuAnswerDTO();
        dto.setTag(tag);
        dto.setContent(content);
        dto.setIsRight(right);
        return dto;
    }

    private String toSafeHtml(String value) {
        return StringUtils.isBlank(value) ? null
                : "<p>" + HtmlUtils.htmlEscape(value).replace("\n", "<br>") + "</p>";
    }

    private String normalizeTags(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return Arrays.stream(value.replace('，', ',').split(","))
                .map(String::trim).filter(StringUtils::isNotBlank).distinct()
                .collect(Collectors.joining(","));
    }

    private String cleanHeader(String header) {
        return header.replace("*", "");
    }

    private static class QuestionRow {
        private final int rowNumber;
        private final List<String> rawValues;
        private final String externalCode;
        private final String questionTypeLabel;
        private final String content;
        private final List<String> options;
        private final String correctAnswer;
        private final String difficultyLabel;
        private final String explanation;
        private final String gradingCriteria;
        private final String tags;
        private String questionType;
        private String difficulty;
        private Set<String> answerLabels = Set.of();

        private QuestionRow(int rowNumber, List<String> values) {
            this.rowNumber = rowNumber;
            this.rawValues = List.copyOf(values);
            this.externalCode = values.get(0);
            this.questionTypeLabel = values.get(1);
            this.content = values.get(2);
            this.options = List.copyOf(values.subList(3, 9));
            this.correctAnswer = values.get(9);
            this.difficultyLabel = values.get(10);
            this.explanation = values.get(11);
            this.gradingCriteria = values.get(12);
            this.tags = values.get(13);
        }
    }

    private static class ValidationBundle {
        private int totalCount;
        private String templateVersion = TEMPLATE_VERSION;
        private final List<QuestionRow> validRows = new ArrayList<>();
        private final List<QuestionImportIssueDTO> issues = new ArrayList<>();
        private final Set<Integer> failedRows = new HashSet<>();
        private final Set<Integer> duplicateRows = new HashSet<>();
        private final Map<Integer, QuestionRow> rowsByNumber = new LinkedHashMap<>();

        private void addIssue(QuestionRow row, String field, String message, String issueType) {
            issues.add(QuestionImportIssueDTO.builder().rowNumber(row.rowNumber)
                    .questionCode(row.externalCode)
                    .content(row.content.length() > 80 ? row.content.substring(0, 80) + "..." : row.content)
                    .field(field).message(message).issueType(issueType).build());
            if ("DUPLICATE".equals(issueType)) {
                duplicateRows.add(row.rowNumber);
            } else {
                failedRows.add(row.rowNumber);
            }
        }

        private QuestionImportPreviewRespDTO toPreview() {
            issues.sort(Comparator.comparing(QuestionImportIssueDTO::getRowNumber)
                    .thenComparing(QuestionImportIssueDTO::getIssueType));
            return QuestionImportPreviewRespDTO.builder().totalCount(totalCount)
                    .validCount(validRows.size()).duplicateCount(duplicateRows.size())
                    .failureCount(failedRows.size()).templateVersion(templateVersion)
                    .issues(issues)
                    .questions(templateVersion.startsWith("WORD_") ? rowsByNumber.values().stream().map(row ->
                            WordQuestionPreviewDTO.builder().paragraph(row.rowNumber).questionCode(row.externalCode)
                                    .questionType(row.questionTypeLabel).content(row.content).options(row.options)
                                    .answer(row.correctAnswer).difficulty(row.difficultyLabel).explanation(row.explanation)
                                    .gradingCriteria(row.gradingCriteria)
                                    .status(failedRows.contains(row.rowNumber) ? "ERROR"
                                            : duplicateRows.contains(row.rowNumber) ? "DUPLICATE" : "VALID").build())
                            .toList() : null).build();
        }

        private List<List<String>> toErrorReportRows() {
            Map<Integer, List<QuestionImportIssueDTO>> grouped = issues.stream()
                    .collect(Collectors.groupingBy(QuestionImportIssueDTO::getRowNumber,
                            LinkedHashMap::new, Collectors.toList()));
            List<List<String>> rows = new ArrayList<>();
            grouped.forEach((rowNumber, rowIssues) -> {
                QuestionRow source = rowsByNumber.get(rowNumber);
                if (source == null) {
                    return;
                }
                boolean error = rowIssues.stream().anyMatch(issue -> "ERROR".equals(issue.getIssueType()));
                List<String> values = new ArrayList<>(source.rawValues);
                values.add(error ? "格式错误" : "重复跳过");
                values.add(rowIssues.stream().map(QuestionImportIssueDTO::getField)
                        .distinct().collect(Collectors.joining("、")));
                values.add(rowIssues.stream().map(QuestionImportIssueDTO::getMessage)
                        .distinct().collect(Collectors.joining("；")));
                values.add(String.valueOf(rowNumber));
                rows.add(values);
            });
            return rows;
        }
    }
}
