package com.yf.modules.exam.repo.service.impl;

import com.yf.base.api.exception.ServiceException;
import org.apache.poi.xwpf.usermodel.*;
import org.w3c.dom.Node;
import java.io.InputStream;
import java.util.*;
import java.util.regex.*;

/** Strict paragraph-based DOCX reader. Never infer a missing question boundary. */
public class WordQuestionParser {
    private static final Pattern START = Pattern.compile("^(\\d{1,6})[.．、]\\s*【([^】]+)】\\s*(.*)$");
    private static final Pattern POSSIBLE_START = Pattern.compile("^(?:\\d+[.．、)]|【(?:单选|多选|判断|简答))");
    private static final Pattern OPTION = Pattern.compile("^([A-Za-z])[.．、]\\s*(.*)$");
    private static final Pattern FIELD = Pattern.compile("^(答案|正确答案|难度|解析|参考答案|评分要点|标签)[：:]\\s*(.*)$");
    private static final Set<String> TYPES = Set.of("单选题", "多选题", "判断题", "简答题");
    private static final Set<String> UNSUPPORTED = Set.of("drawing", "pict", "object", "oMath", "oMathPara",
            "sdt", "altChunk", "ins", "del", "footnoteReference", "endnoteReference", "fldSimple", "fldChar");

    public record Issue(String field, String message) {}
    public record ParsedQuestion(int paragraph, List<String> values, List<Issue> issues) {}

    public List<ParsedQuestion> parse(InputStream input, String defaultDifficulty) {
        try (XWPFDocument doc = new XWPFDocument(input)) {
            List<ParsedQuestion> result = new ArrayList<>();
            Draft current = null;
            Set<String> numbers = new HashSet<>();
            int paragraph = 0, characters = 0;
            for (IBodyElement element : doc.getBodyElements()) {
                paragraph++;
                if (paragraph > 20000) throw new ServiceException("Word 段落过多，请拆分文件");
                if (!(element instanceof XWPFParagraph p)) {
                    throw location(paragraph, "暂不支持表格或内容控件，请改成普通文字段落", element.getElementType().name());
                }
                String text = p.getText().replace('\u00a0', ' ').replace('\u3000', ' ').trim();
                if (p.getNumID() != null) throw location(paragraph, "不支持自动编号，请将题号和选项编号改为手工输入", text);
                if (unsupported(p.getCTP().getDomNode())) {
                    throw location(paragraph, "含图片、公式、域、修订或内容控件，请转换为普通文字", text);
                }
                if (text.isEmpty()) continue;
                characters += text.length();
                if (characters > 2_000_000) throw new ServiceException("Word 文字过多，请拆分文件");
                Matcher start = START.matcher(text);
                if (start.matches()) {
                    if (current != null) result.add(current.finish(defaultDifficulty));
                    if (result.size() >= 1000) throw new ServiceException("单次最多导入 1000 道题，请拆分文件");
                    current = new Draft(paragraph, start.group(1), start.group(2).trim(), start.group(3));
                    if (!numbers.add(start.group(1))) current.error("题目编号", paragraph, "题号重复，请使用不同题号");
                    if (!TYPES.contains(current.values.get(1))) current.error("题型", paragraph, "仅支持单选题、多选题、判断题和简答题");
                    continue;
                }
                if (current == null || POSSIBLE_START.matcher(text).find()) {
                    throw location(paragraph, "无法确定题目边界，请按“1.【单选题】题干”格式编写", text);
                }
                // Soft line breaks are allowed only as continuation, never as hidden field boundaries.
                for (String line : text.split("\\R")) {
                    Matcher option = OPTION.matcher(line.trim());
                    Matcher field = FIELD.matcher(line.trim());
                    if (text.contains("\n") && (option.matches() || field.matches() || START.matcher(line.trim()).matches())) {
                        throw location(paragraph, "选项、答案和下一道题必须分别独立成段，请用 Enter 换段", text);
                    }
                }
                Matcher option = OPTION.matcher(text);
                Matcher field = FIELD.matcher(text);
                if (option.matches()) {
                    int index = Character.toUpperCase(option.group(1).charAt(0)) - 'A';
                    if (index > 5) {
                        current.error("选项", paragraph, "选项仅支持 A-F");
                        current.active = -1;
                    } else {
                        if (current.fieldsStarted) current.error("选项", paragraph, "选项必须写在答案、难度和解析之前");
                        if (index != current.nextOption) current.error("选项", paragraph, "选项必须从 A 开始连续排列，不能重复或倒序");
                        current.nextOption = index + 1;
                        current.assign(index + 3, option.group(2), paragraph);
                    }
                } else if (field.matches()) {
                    current.fieldsStarted = true;
                    String name = field.group(1);
                    int index = switch (name) {
                        case "答案", "正确答案" -> 9;
                        case "难度" -> 10;
                        case "解析", "参考答案" -> 11;
                        case "评分要点" -> 12;
                        default -> 13;
                    };
                    if (name.equals("参考答案") && !current.values.get(1).equals("简答题")) {
                        current.error("参考答案", paragraph, "客观题请使用“答案：”和“解析：”");
                    }
                    if (name.equals("解析") && current.values.get(1).equals("简答题")) {
                        current.error("解析", paragraph, "简答题请使用“参考答案：”");
                    }
                    current.assign(index, field.group(2), paragraph);
                } else if (current.active == 2 || (current.active >= 3 && current.active <= 8)
                        || current.active == 11 || current.active == 12) {
                    current.append(text);
                } else {
                    current.error("段落", paragraph, "无法识别的内容：" + excerpt(text));
                }
            }
            if (current != null) result.add(current.finish(defaultDifficulty));
            if (result.isEmpty()) throw new ServiceException("Word 中没有题目，请按模板填写后上传");
            return result;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Word 读取失败，请确认是未加密、未损坏的 .docx 文件");
        }
    }

    private boolean unsupported(Node node) {
        if (node.getLocalName() != null && UNSUPPORTED.contains(node.getLocalName())) return true;
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (unsupported(child)) return true;
        }
        return false;
    }

    private static String excerpt(String text) { return text.length() > 100 ? text.substring(0, 100) + "…" : text; }
    private static ServiceException location(int paragraph, String message, String text) {
        return new ServiceException("第 " + paragraph + " 段：" + message + "。原文：" + excerpt(text));
    }

    private static class Draft {
        final int paragraph;
        final List<String> values = new ArrayList<>(Collections.nCopies(14, ""));
        final List<Issue> issues = new ArrayList<>();
        final Set<Integer> assigned = new HashSet<>();
        int active = 2, nextOption = 0;
        boolean fieldsStarted;
        Draft(int paragraph, String number, String type, String content) {
            this.paragraph = paragraph;
            values.set(0, number); values.set(1, type); values.set(2, content.trim());
        }
        void error(String field, int at, String message) { issues.add(new Issue(field, "第 " + at + " 段：" + message)); }
        void assign(int index, String text, int at) {
            if (!assigned.add(index)) {
                error("字段", at, "同一题不能重复填写选项、答案、难度、解析等字段");
                active = -1;
                return;
            }
            values.set(index, text.trim()); active = index;
        }
        void append(String text) { values.set(active, values.get(active) + "\n" + text); }
        ParsedQuestion finish(String difficulty) {
            if (values.get(10).isBlank() && difficulty != null) values.set(10, difficulty);
            return new ParsedQuestion(paragraph, List.copyOf(values), List.copyOf(issues));
        }
    }
}
