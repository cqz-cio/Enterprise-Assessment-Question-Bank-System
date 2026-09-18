package com.yf.modules.exam.repo.service.impl;

import com.yf.base.api.exception.ServiceException;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import java.io.*;
import java.math.BigInteger;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WordQuestionParserTest {
    private final WordQuestionParser parser = new WordQuestionParser();

    static byte[] document(String... paragraphs) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : paragraphs) doc.createParagraph().createRun().setText(text);
            doc.write(out); return out.toByteArray();
        }
    }
    private List<WordQuestionParser.ParsedQuestion> parse(String difficulty, String... paragraphs) throws IOException {
        return parser.parse(new ByteArrayInputStream(document(paragraphs)), difficulty);
    }

    @Test void readsMultilineAndPunctuationWithoutGuessing() throws Exception {
        var rows = parse("一般", "1．【单选题】第一行", "第二行", "A．选项一", "选项一续行", "B. 选项二", "答案: A", "解析：说明", "说明续行");
        assertEquals(1, rows.size());
        assertEquals("第一行\n第二行", rows.get(0).values().get(2));
        assertEquals("选项一\n选项一续行", rows.get(0).values().get(3));
        assertEquals("一般", rows.get(0).values().get(10));
        assertEquals("说明\n说明续行", rows.get(0).values().get(11));
        assertTrue(rows.get(0).issues().isEmpty());
    }
    @Test void templateIsDirectlyParseableAndIncludesFourTypes() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new WordQuestionTemplateWriter().write(response);
        var rows = parser.parse(new ByteArrayInputStream(response.getContentAsByteArray()), null);
        assertEquals(List.of("单选题", "多选题", "判断题", "简答题"), rows.stream().map(r -> r.values().get(1)).toList());
        assertTrue(rows.stream().allMatch(r -> r.issues().isEmpty()));
    }
    @Test void missingTypeBlocksAmbiguousBoundaries() {
        assertThrows(ServiceException.class, () -> parse(null, "1.【单选题】题干", "A. 一", "B. 二", "答案：A", "2. 缺少题型的新题"));
        assertThrows(ServiceException.class, () -> parse(null, "前言内容", "1.【判断题】题干"));
    }
    @Test void duplicateFieldsAndOutOfOrderOptionsAreErrors() throws Exception {
        var rows = parse(null, "1.【单选题】题干", "B. 二", "A. 一", "答案：A", "答案：B", "难度：简单");
        assertTrue(rows.get(0).issues().size() >= 3);
        assertEquals("A", rows.get(0).values().get(9));
    }
    @Test void fallbackDoesNotOverrideExplicitDifficulty() throws Exception {
        var rows = parse("一般", "1.【判断题】第一题", "答案：正确", "难度：较难", "2.【判断题】第二题", "答案：错误");
        assertEquals("较难", rows.get(0).values().get(10));
        assertEquals("一般", rows.get(1).values().get(10));
        assertEquals(4, rows.get(1).paragraph());
    }
    @Test void extraTextAfterAnswerIsReported() throws Exception {
        var row = parse("简单", "1.【判断题】题干", "答案：正确", "不属于任何字段的文字").get(0);
        assertTrue(row.issues().get(0).message().contains("第 3 段"));
    }
    @Test void tableAutoNumberAndFormulaAreRejected() throws Exception {
        for (String kind : List.of("table", "number", "formula", "drawing")) {
            try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                var p = doc.createParagraph(); p.createRun().setText("1.【判断题】题干");
                if (kind.equals("table")) doc.createTable();
                if (kind.equals("number")) p.setNumID(BigInteger.ONE);
                if (kind.equals("formula") || kind.equals("drawing")) {
                    var node = p.getCTP().getDomNode();
                    node.appendChild(node.getOwnerDocument().createElementNS(
                            kind.equals("formula") ? "http://schemas.openxmlformats.org/officeDocument/2006/math" : "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
                            kind.equals("formula") ? "m:oMath" : "w:drawing"));
                }
                doc.write(out);
                assertThrows(ServiceException.class, () -> parser.parse(new ByteArrayInputStream(out.toByteArray()), null), kind);
            }
        }
    }
    @Test void rejectsEmptyCorruptAndTooManyQuestions() throws Exception {
        assertThrows(ServiceException.class, () -> parse(null, "", " "));
        assertThrows(ServiceException.class, () -> parser.parse(new ByteArrayInputStream(new byte[]{1, 2}), null));
        String[] rows = new String[1001];
        for (int i = 0; i < rows.length; i++) rows[i] = (i + 1) + ".【判断题】题干";
        assertThrows(ServiceException.class, () -> parse(null, rows));
    }
}
