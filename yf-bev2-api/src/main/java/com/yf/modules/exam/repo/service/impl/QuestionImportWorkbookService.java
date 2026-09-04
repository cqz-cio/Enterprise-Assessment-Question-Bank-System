package com.yf.modules.exam.repo.service.impl;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class QuestionImportWorkbookService {

    public void writeTemplate(HttpServletResponse response) throws IOException {
        try (XSSFWorkbook workbook = createTemplateWorkbook()) {
            prepareDownload(response, "试题批量导入模板_V1.xlsx");
            workbook.write(response.getOutputStream());
        }
    }

    public void writeErrorReport(HttpServletResponse response, List<List<String>> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle header = createHeaderStyle(workbook);
            CellStyle body = createBodyStyle(workbook);
            Sheet sheet = workbook.createSheet("错误数据");
            List<String> headers = new ArrayList<>(QuestionImportServiceImpl.HEADERS);
            headers.addAll(List.of("处理结果", "错误字段", "错误原因"));
            writeHeader(sheet, headers, header);
            writeRows(sheet, 1, rows, body);
            setQuestionColumns(sheet);
            sheet.setColumnWidth(14, 16 * 256);
            sheet.setColumnWidth(15, 24 * 256);
            sheet.setColumnWidth(16, 60 * 256);
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(1, rows.size()), 0, headers.size() - 1));
            prepareDownload(response, "试题导入错误报告.xlsx");
            workbook.write(response.getOutputStream());
        }
    }

    private XSSFWorkbook createTemplateWorkbook() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        CellStyle header = createHeaderStyle(workbook);
        CellStyle body = createBodyStyle(workbook);
        CellStyle title = createTitleStyle(workbook);
        CellStyle section = createSectionStyle(workbook);
        createGuideSheet(workbook, title, section, body);
        createDataSheet(workbook, header);
        createExampleSheet(workbook, header, body);
        createDictionarySheet(workbook, header, body);
        workbook.setActiveSheet(0);
        return workbook;
    }

    private void createGuideSheet(XSSFWorkbook workbook, CellStyle title, CellStyle section, CellStyle body) {
        Sheet sheet = workbook.createSheet("填写说明");
        sheet.setDisplayGridlines(false);
        sheet.addMergedRegion(new CellRangeAddress(0, 1, 0, 7));
        Cell titleCell = sheet.createRow(0).createCell(0);
        titleCell.setCellValue("企业考核系统｜试题批量导入模板 V1");
        titleCell.setCellStyle(title);
        createMergedText(sheet, 3, 3, 0, 7, "使用方式", section);
        createMergedText(sheet, 4, 8, 0, 7,
                "1. 在试题管理页面选择目标题库后下载本模板。\n"
                        + "2. 只在“试题数据”工作表中填写，一行代表一道题。\n"
                        + "3. 请勿修改表头、增加列、合并单元格或使用公式。\n"
                        + "4. 上传后系统先校验预览，确认后将正确题目导入并默认启用。\n"
                        + "5. 同一题库内重复题自动跳过；错误行可下载报告后修正重传。", body);
        createMergedText(sheet, 10, 10, 0, 7, "答案填写规则", section);
        writeRows(sheet, 11, List.of(
                List.of("题型", "填写要求"),
                List.of("单选题", "至少两个选项；正确答案填写一个字母，例如 B。"),
                List.of("多选题", "至少两个选项；答案使用英文逗号分隔，例如 A,C。"),
                List.of("判断题", "选项留空；正确答案只能填写“正确”或“错误”。"),
                List.of("简答题", "选项和正确答案留空；可填写参考答案和评分要点。")
        ), body);
        createMergedText(sheet, 17, 19, 0, 7,
                "注意：题目分值不在本模板中维护，由考核模板的组卷规则统一设置。导入的简答题会进入题库并默认启用；完整作答和人工阅卷流程在简答题阶段实现。", body);
        sheet.setColumnWidth(0, 18 * 256);
        sheet.setColumnWidth(1, 72 * 256);
        for (int index = 2; index < 8; index++) {
            sheet.setColumnWidth(index, 15 * 256);
        }
        sheet.createFreezePane(0, 2);
    }

    private void createDataSheet(XSSFWorkbook workbook, CellStyle header) {
        Sheet sheet = workbook.createSheet(QuestionImportServiceImpl.DATA_SHEET_NAME);
        writeHeader(sheet, QuestionImportServiceImpl.HEADERS, header);
        setQuestionColumns(sheet);
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, QuestionImportServiceImpl.MAX_DATA_ROWS, 0,
                QuestionImportServiceImpl.HEADERS.size() - 1));
        addListValidation(sheet, 1, QuestionImportServiceImpl.MAX_DATA_ROWS, 1,
                new String[]{"单选题", "多选题", "判断题", "简答题"});
        addListValidation(sheet, 1, QuestionImportServiceImpl.MAX_DATA_ROWS, 10,
                new String[]{"简单", "一般", "较难", "极难"});
    }

    private void createExampleSheet(XSSFWorkbook workbook, CellStyle header, CellStyle body) {
        Sheet sheet = workbook.createSheet("填写示例");
        writeHeader(sheet, QuestionImportServiceImpl.HEADERS, header);
        writeRows(sheet, 1, List.of(
                List.of("Q001", "单选题", "客户提出异议时，首先应该怎么做？", "立即反驳", "了解异议原因", "立即降价", "结束沟通", "", "", "B", "一般", "应先了解客户的真实诉求。", "", "客户沟通"),
                List.of("Q002", "多选题", "下列哪些属于餐具？", "餐盘", "沙发", "筷子", "衣柜", "", "", "A,C", "简单", "餐盘和筷子属于餐具。", "", "产品知识"),
                List.of("Q003", "判断题", "员工可以将客户资料发送给无关人员。", "", "", "", "", "", "", "错误", "简单", "客户资料属于公司敏感信息。", "", "企业制度"),
                List.of("Q004", "简答题", "请说明处理客户投诉的基本步骤。", "", "", "", "", "", "", "", "一般", "倾听、确认问题、提出方案、跟踪回访。", "倾听2分；确认2分；方案4分；回访2分。", "沟通能力")
        ), body);
        setQuestionColumns(sheet);
        sheet.createFreezePane(0, 1);
    }

    private void createDictionarySheet(XSSFWorkbook workbook, CellStyle header, CellStyle body) {
        Sheet sheet = workbook.createSheet("字段字典");
        writeHeader(sheet, List.of("题型", "难度", "正确答案示例", "说明"), header);
        writeRows(sheet, 1, List.of(
                List.of("单选题", "简单", "B", "答案必须对应已填写的选项"),
                List.of("多选题", "一般", "A,C", "多个答案使用英文逗号分隔"),
                List.of("判断题", "较难", "正确 / 错误", "判断题不填写选项"),
                List.of("简答题", "极难", "留空", "填写参考答案和评分要点")
        ), body);
        sheet.setColumnWidth(0, 18 * 256);
        sheet.setColumnWidth(1, 18 * 256);
        sheet.setColumnWidth(2, 22 * 256);
        sheet.setColumnWidth(3, 42 * 256);
    }

    private void writeHeader(Sheet sheet, List<String> headers, CellStyle style) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(28);
        for (int index = 0; index < headers.size(); index++) {
            Cell cell = row.createCell(index, CellType.STRING);
            cell.setCellValue(headers.get(index));
            cell.setCellStyle(style);
        }
    }

    private void writeRows(Sheet sheet, int startRow, List<List<String>> rows, CellStyle style) {
        for (int rowOffset = 0; rowOffset < rows.size(); rowOffset++) {
            Row row = sheet.getRow(startRow + rowOffset);
            if (row == null) {
                row = sheet.createRow(startRow + rowOffset);
            }
            row.setHeightInPoints(42);
            List<String> values = rows.get(rowOffset);
            for (int column = 0; column < values.size(); column++) {
                Cell cell = row.createCell(column, CellType.STRING);
                cell.setCellValue(values.get(column));
                cell.setCellStyle(style);
            }
        }
    }

    private void createMergedText(Sheet sheet, int firstRow, int lastRow, int firstColumn,
                                  int lastColumn, String value, CellStyle style) {
        sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, firstColumn, lastColumn));
        Row row = sheet.getRow(firstRow);
        if (row == null) {
            row = sheet.createRow(firstRow);
        }
        Cell cell = row.createCell(firstColumn, CellType.STRING);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setQuestionColumns(Sheet sheet) {
        int[] widths = {14, 12, 42, 16, 16, 16, 16, 16, 16, 15, 12, 36, 36, 20};
        for (int index = 0; index < widths.length; index++) {
            sheet.setColumnWidth(index, widths[index] * 256);
        }
    }

    private void addListValidation(Sheet sheet, int firstRow, int lastRow, int column, String[] values) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(values);
        DataValidation validation = helper.createValidation(constraint,
                new CellRangeAddressList(firstRow, lastRow, column, column));
        validation.setShowErrorBox(true);
        validation.createErrorBox("填写错误", "请选择模板提供的固定值");
        sheet.addValidationData(validation);
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        setThinBorders(style);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private CellStyle createBodyStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        setThinBorders(style);
        DataFormat dataFormat = workbook.createDataFormat();
        style.setDataFormat(dataFormat.getFormat("@"));
        return style;
    }

    private CellStyle createTitleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 18);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private CellStyle createSectionStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        return style;
    }

    private void setThinBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private void prepareDownload(HttpServletResponse response, String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
    }
}
