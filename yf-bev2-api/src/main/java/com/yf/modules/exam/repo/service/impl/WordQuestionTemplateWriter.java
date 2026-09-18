package com.yf.modules.exam.repo.service.impl;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.xwpf.usermodel.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class WordQuestionTemplateWriter {
    public void write(HttpServletResponse response) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            document.getProperties().getCoreProperties().setTitle("Word 试题导入模板 V1（请替换示例内容）");
            List<String> paragraphs = List.of(
                    "1.【单选题】中国的首都是哪里？", "A. 北京", "B. 上海", "C. 广州", "D. 深圳",
                    "答案：A", "难度：简单", "解析：北京是中国的首都。", "",
                    "2.【多选题】下列哪些属于餐具？", "A. 餐盘", "B. 沙发", "C. 筷子",
                    "答案：A,C", "难度：一般", "解析：餐盘和筷子属于餐具。", "",
                    "3.【判断题】员工可以将客户资料发送给无关人员。", "答案：错误", "难度：简单",
                    "解析：客户资料需要妥善保管。", "",
                    "4.【简答题】请说明处理客户投诉的基本步骤。", "难度：较难",
                    "参考答案：倾听、确认问题、提出方案、跟踪回访。", "评分要点：覆盖倾听、确认、方案和回访。"
            );
            for (String text : paragraphs) {
                XWPFParagraph p = document.createParagraph();
                p.setSpacingAfter(80);
                XWPFRun run = p.createRun();
                run.setFontFamily("Microsoft YaHei");
                run.setFontSize(11);
                if (text.matches("^\\d+.*")) run.setBold(true);
                run.setText(text);
            }
            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            String name = URLEncoder.encode("Word试题导入模板_V1.docx", StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + name);
            document.write(response.getOutputStream());
        }
    }
}
