package com.yf.modules.exam.report;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class ResultWorkbook {
    private ResultWorkbook() {}
    public static final String[] HEADERS={"姓名","人员类型","编号/工号","部门","岗位","场景","批次","考核名称","客观分","主观分","最终总分","满分","及格分","是否通过","交卷时间","阅卷状态","终审人","完成阅卷时间"};
    public static byte[] write(List<Map<String,Object>> rows) {
        try(var book=new XSSFWorkbook(); var output=new ByteArrayOutputStream()) {
            var sheet=book.createSheet("成绩查询"); var header=sheet.createRow(0);
            var bold=book.createFont(); bold.setBold(true); var style=book.createCellStyle(); style.setFont(bold);
            for(int i=0;i<HEADERS.length;i++) { header.createCell(i).setCellValue(HEADERS[i]); header.getCell(i).setCellStyle(style); sheet.setColumnWidth(i,(i==7?32:i==14||i==17?22:16)*256); }
            int n=1;
            for(var r:rows) {
                Object[] values={r.get("subjectName"),"CANDIDATE".equals(r.get("subjectType"))?"候选人":"员工",r.get("subjectNo"),r.get("departName"),r.get("positionName"),scene(r.get("sceneType")),r.get("batchNo"),r.get("title"),r.get("objectiveScore"),r.get("subjectiveScore"),r.get("userScore"),r.get("totalScore"),r.get("qualifyScore"),r.get("passed")==null?null:truth(r.get("passed"))?"通过":"未通过",date(r.get("handTime")),state(r),r.get("graderName"),date(r.get("gradedAt"))};
                var row=sheet.createRow(n++);
                for(int i=0;i<values.length;i++) { var cell=row.createCell(i); Object v=values[i]; if(v instanceof Number number) cell.setCellValue(number.doubleValue()); else if(v!=null) cell.setCellValue(v.toString()); }
            }
            sheet.createFreezePane(0,1); sheet.setAutoFilter(new CellRangeAddress(0,n-1,0,HEADERS.length-1)); book.write(output); return output.toByteArray();
        } catch(IOException e) { throw new IllegalStateException("成绩文件生成失败",e); }
    }
    static boolean truth(Object v) { return Boolean.TRUE.equals(v)||v instanceof Number n&&n.intValue()!=0; }
    static String scene(Object v) { return Map.of("INTERVIEW","面试","REGULARIZATION","转正","PROMOTION","晋升").getOrDefault(String.valueOf(v),""); }
    static String state(Map<String,Object> r) { return switch(String.valueOf(r.get("gradingState"))) { case "NOT_REQUIRED" -> "自动评分"; case "GRADED" -> "已完成阅卷"; default -> ((Number)r.getOrDefault("gradedCount",0)).intValue()>0?"阅卷中":"待阅卷"; }; }
    static String date(Object v) { if(v==null)return null; if(v instanceof Date date) { var fmt=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); fmt.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai")); return fmt.format(date); } return v.toString(); }
}
