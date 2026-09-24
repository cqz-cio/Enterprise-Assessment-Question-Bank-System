package com.yf.modules.exam.assignment.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yf.base.api.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Function;
import static com.yf.modules.exam.assignment.importing.CandidateImportModels.ImportRow;

/** Durable, authenticated-encrypted import results. No credential is stored in plaintext. */
@Component
public class CandidateImportArchive {
    public static final long PREVIEW_TTL = 15 * 60_000L;
    public static final long RESULT_TTL = 30L * 24 * 60 * 60_000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final SecretKeySpec key;

    public static class Task {
        public String id, owner, filename, fileHash;
        public long expires;
        public boolean committed;
        public List<ImportRow> rows = new ArrayList<>();
    }

    public CandidateImportArchive(JdbcTemplate db, ObjectMapper json, PlatformTransactionManager manager,
            @Value("${security.assignment-code.pepper:}") String pepper) {
        this.db=db; this.json=json;
        transaction=new TransactionTemplate(manager); transaction.setTimeout(30);
        if(pepper==null || pepper.isBlank() || pepper.length()<32)
            throw new IllegalStateException("考核码归档密钥未配置，拒绝启动");
        try {
            Mac derivation=Mac.getInstance("HmacSHA256");
            derivation.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            key=new SecretKeySpec(derivation.doFinal("enterprise-exam/candidate-import/archive/aes-gcm/v1".getBytes(StandardCharsets.UTF_8)),"AES");
        } catch(Exception ex) { throw new IllegalStateException("考核码归档加密初始化失败"); }
    }

    private byte[] aad(String id,String owner,String fileHash,long expires,boolean committed) {
        return String.join("\n","v1",id,owner,fileHash,Long.toString(expires),Boolean.toString(committed)).getBytes(StandardCharsets.UTF_8);
    }
    private String encrypt(Task t) {
        try {
            byte[] nonce=new byte[12]; RANDOM.nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));
            cipher.updateAAD(aad(t.id,t.owner,t.fileHash,t.expires,t.committed));
            byte[] encrypted=cipher.doFinal(json.writeValueAsBytes(t));
            byte[] envelope=Arrays.copyOf(nonce,nonce.length+encrypted.length);
            System.arraycopy(encrypted,0,envelope,nonce.length,encrypted.length);
            return "v1:"+Base64.getEncoder().encodeToString(envelope);
        } catch(Exception ex) { throw new ServiceException("清单加密保存失败，本行未发放，请重试"); }
    }
    private Task decode(Map<String,Object> row) {
        try {
            Object raw=row.get("payload");
            String payload=raw instanceof java.sql.Clob clob?clob.getSubString(1,(int)clob.length()):(String)raw;
            if(!payload.startsWith("v1:")) throw new IllegalArgumentException();
            byte[] envelope=Base64.getDecoder().decode(payload.substring(3));
            if(envelope.length<29) throw new IllegalArgumentException();
            boolean committed=((Number)row.get("committed")).intValue()!=0;
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOf(envelope,12)));
            cipher.updateAAD(aad((String)row.get("id"),(String)row.get("owner_id"),(String)row.get("file_hash"),((Number)row.get("expires_at")).longValue(),committed));
            return json.readValue(cipher.doFinal(envelope,12,envelope.length-12),Task.class);
        } catch(Exception ex) { throw new ServiceException("清单无法解密，请联系管理员核对原密钥和归档完整性"); }
    }
    public Task get(String id,String owner) {
        return load(id,owner,false);
    }
    private Task load(String id,String owner,boolean lock) {
        var rows=db.queryForList("SELECT * FROM el_candidate_import_task WHERE id=? AND owner_id=? AND expires_at>?"+(lock?" FOR UPDATE":""),id,owner,System.currentTimeMillis());
        if(rows.isEmpty()) throw new ServiceException("导入任务不存在、已过期或无权访问，请重新校验文件");
        return decode(rows.get(0));
    }
    public Task find(String owner,String fileHash) {
        var rows=fileHash==null
                ? db.queryForList("SELECT * FROM el_candidate_import_task WHERE owner_id=? AND expires_at>? ORDER BY created_at DESC,id DESC LIMIT 1",owner,System.currentTimeMillis())
                : db.queryForList("SELECT * FROM el_candidate_import_task WHERE owner_id=? AND file_hash=? AND expires_at>? ORDER BY created_at DESC,id DESC LIMIT 1",owner,fileHash,System.currentTimeMillis());
        return rows.isEmpty()?null:decode(rows.get(0));
    }
    public <T> T locked(String id,String owner,Function<Task,T> action) {
        return transaction.execute(status->action.apply(load(id,owner,true)));
    }
    public void create(Task task) {
        transaction.executeWithoutResult(status->{
            // Serialize this account's preview quota without holding any business assignment locks.
            db.queryForObject("SELECT id FROM el_sys_user WHERE id=? FOR UPDATE",String.class,task.owner);
            Integer previews=db.queryForObject("SELECT COUNT(*) FROM el_candidate_import_task WHERE owner_id=? AND committed=0 AND expires_at>?",Integer.class,task.owner,System.currentTimeMillis());
            if(previews!=null && previews>=10) throw new ServiceException("待确认任务过多，请关闭旧任务或 15 分钟后重试");
            db.update("INSERT INTO el_candidate_import_task(id,owner_id,file_hash,committed,expires_at,created_at,payload) VALUES(?,?,?,?,?,?,?)",task.id,task.owner,task.fileHash,0,task.expires,System.currentTimeMillis(),encrypt(task));
        });
    }
    public void save(Task task) {
        if(db.update("UPDATE el_candidate_import_task SET committed=?,expires_at=?,payload=? WHERE id=? AND owner_id=?",task.committed?1:0,task.expires,encrypt(task),task.id,task.owner)!=1)
            throw new ServiceException("清单保存失败，请重试获取本次结果");
    }
    public void close(String id,String owner) {
        locked(id,owner,t->{
            // A partially completed import must also remain recoverable.
            if(!t.committed && t.rows.stream().noneMatch(r->"SUCCESS".equals(r.getStatus())))
                db.update("DELETE FROM el_candidate_import_task WHERE id=? AND owner_id=?",id,owner);
            return null;
        });
    }
    public void cleanup() {
        db.update("DELETE FROM el_candidate_import_task WHERE expires_at<?",System.currentTimeMillis());
    }
}
