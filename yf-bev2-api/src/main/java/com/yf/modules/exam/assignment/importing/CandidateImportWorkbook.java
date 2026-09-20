package com.yf.modules.exam.assignment.importing;

import com.yf.base.api.exception.ServiceException;
import com.yf.modules.exam.assignment.importing.CandidateImportModels.ImportRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipInputStream;

public final class CandidateImportWorkbook {
    public static final List<String> HEADERS = List.of("姓名","候选人编号","手机号","邮箱","部门编码","岗位编码","批次","生效时间","截止时间");
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private CandidateImportWorkbook() { }

    public static List<ImportRow> read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ServiceException("请选择 Excel 文件");
        if (file.getSize() > 5L * 1024 * 1024) throw new ServiceException("文件不能超过 5 MB");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new ServiceException("仅支持 .xlsx 文件");
        checkExpandedSize(file);
        try (var in = file.getInputStream(); var book = new XSSFWorkbook(in)) {
            Sheet sheet = book.getSheet("候选人数据");
            if (sheet == null || sheet.getRow(0) == null) throw new ServiceException("请使用标准模板的候选人数据工作表");
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            for (int i=0;i<HEADERS.size();i++) {
                Cell cell = sheet.getRow(0).getCell(i);
                if (cell == null || cell.getCellType() == CellType.FORMULA || !HEADERS.get(i).equals(formatter.formatCellValue(cell).trim()))
                    throw new ServiceException("模板表头不匹配，请重新下载标准模板");
            }
            if (sheet.getLastRowNum() > 5000) throw new ServiceException("工作表范围过大，请删除多余空行；单次最多 500 条");
            List<ImportRow> rows = new ArrayList<>();
            for (int i=1;i<=sheet.getLastRowNum();i++) {
                Row row = sheet.getRow(i); if (row == null) continue;
                var item = new ImportRow(); item.setRowNumber(i+1);
                boolean formula = false, oversized = false;
                for (int j=0;j<9;j++) {
                    Cell cell = row.getCell(j); String value = "";
                    if (cell != null) {
                        formula |= cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR;
                        if (j>=7 && cell.getCellType()==CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))
                            value = DATE.format(cell.getLocalDateTimeCellValue());
                        else value = formatter.formatCellValue(cell).trim();
                    }
                    oversized |= value.length()>512;
                    item.getValues().add(value.length()>512 ? value.substring(0,512) : value);
                }
                if (item.getValues().stream().allMatch(String::isBlank)) continue;
                if (formula) item.setMessage("不支持公式或错误单元格，请粘贴为文本值");
                if (oversized) item.setMessage("单元格内容过长，单字段不能超过 512 字符");
                rows.add(item);
                if (rows.size()>500) throw new ServiceException("单次最多导入 500 条候选人");
            }
            if (rows.isEmpty()) throw new ServiceException("文件中没有候选人数据");
            return rows;
        } catch (ServiceException ex) { throw ex; }
        catch (Exception ex) { throw new ServiceException("Excel 读取失败，请使用未加密的标准 XLSX 模板"); }
    }

    private static void checkExpandedSize(MultipartFile file) {
        // Bound shared strings/styles and other OOXML parts before POI materializes them.
        try(var zip = new ZipInputStream(file.getInputStream())) {
            long total=0; int entries=0; byte[] buffer=new byte[8192];
            while(zip.getNextEntry()!=null) {
                if(++entries>1000) throw new ServiceException("工作簿结构过于复杂，请使用标准模板");
                int count; while((count=zip.read(buffer))!=-1) {
                    total+=count;
                    if(total>32L*1024*1024) throw new ServiceException("工作簿展开后过大，请删除多余格式、图片与工作表");
                }
            }
        } catch(IOException ex) { throw new ServiceException("Excel 文件损坏，请重新保存为 XLSX"); }
    }

    public static byte[] template(List<List<String>> references) {
        try (var book = new XSSFWorkbook()) {
            sheet(book,"候选人数据",HEADERS,List.of());
            sheet(book,"填写说明",List.of("项目","说明"),List.of(
                    List.of("必填字段","候选人数据工作表 9 列全部必填；请勿修改表头；每批最多 500 行、5 MB"),
                    List.of("编号与手机号","请按文本填写，保留前导零；候选人编号及批次各最多 64 字符"),
                    List.of("时间格式","yyyy-MM-dd HH:mm:ss，例如 2026-09-21 09:00:00；建议截止时间为生效时间加 14 天"),
                    List.of("有效期","截止时间必须晚于生效时间且未过期"),
                    List.of("部门岗位","使用编码参考中的组合；系统匹配唯一启用的 INTERVIEW 模板"),
                    List.of("重复记录","同一候选人编号与同一批次（忽略大小写）重复时跳过；不覆盖或重置旧考核"),
                    List.of("部分成功","有效行独立发放，错误行和重复行可下载报告；确认时再次校验最新状态"),
                    List.of("结果保存","考核码仅在本次导入会话内提供，完成后请及时下载清单；会话 15 分钟有效，重启会失效")));
            sheet(book,"部门岗位编码参考",List.of("部门编码","部门名称","岗位编码","岗位名称"),references);
            return bytes(book);
        } catch(IOException ex) { throw new ServiceException("模板生成失败"); }
    }

    public static byte[] report(List<ImportRow> rows, boolean codes) {
        List<String> headers = codes ? List.of("Excel行号","姓名","候选人编号","部门","岗位","批次","测评","生效时间","截止时间","考核码","入口路径")
                : new ArrayList<>(HEADERS);
        if (!codes) { headers.add(0,"Excel行号"); headers.add("状态"); headers.add("原因"); }
        List<List<String>> data = new ArrayList<>();
        for (ImportRow r:rows) {
            if(codes && !"SUCCESS".equals(r.getStatus())) continue;
            if(!codes && !Set.of("ERROR","DUPLICATE").contains(r.getStatus())) continue;
            var line = new ArrayList<String>(); line.add(String.valueOf(r.getRowNumber()));
            if(codes) Collections.addAll(line,r.getCandidateName(),r.getCandidateNo(),r.getDepartName(),r.getPositionName(),r.getBatchNo(),r.getExamTitle(),r.getValidFrom(),r.getExpireAt(),r.getAccessCode(),"/#/exam-entry");
            else { line.addAll(r.getValues()); line.add("ERROR".equals(r.getStatus())?"失败":"重复跳过"); line.add(r.getMessage()); }
            data.add(line);
        }
        try(var book = new XSSFWorkbook()) { sheet(book,codes?"发放清单":"问题行报告",headers,data); return bytes(book); }
        catch(IOException ex) { throw new ServiceException("清单生成失败"); }
    }

    private static void sheet(XSSFWorkbook book,String name,List<String> headers,List<List<String>> data) {
        Sheet sheet = book.createSheet(name); sheet.createFreezePane(0,1);
        var text = book.createCellStyle(); text.setDataFormat(book.createDataFormat().getFormat("@"));
        var heading = book.createCellStyle(); var font = book.createFont(); font.setBold(true); heading.setFont(font);
        Row head = sheet.createRow(0);
        for(int i=0;i<headers.size();i++) { head.createCell(i).setCellValue(headers.get(i)); head.getCell(i).setCellStyle(heading); sheet.setColumnWidth(i, Math.min(60,Math.max(20,headers.get(i).length()*3))*256); sheet.setDefaultColumnStyle(i,text); }
        int index=1;
        for(var values:data) { Row row=sheet.createRow(index++); for(int i=0;i<values.size();i++) { Cell cell=row.createCell(i,CellType.STRING); cell.setCellValue(Objects.toString(values.get(i),"")); cell.setCellStyle(text); } }
    }
    private static byte[] bytes(XSSFWorkbook book) throws IOException { var out=new ByteArrayOutputStream(); book.write(out); return out.toByteArray(); }
}
