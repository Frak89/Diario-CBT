package com.frak89.diariocbt;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.PBEKeySpec;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.*;

/** Portable, authenticated, password-protected backup independent of AndroidKeystore.
 * Format: CBTBK3\0\0 + PBKDF2 salt(16) + CTR iv(16) + ZIP ciphertext + HMAC-SHA256(32).
 * HMAC checked in constant time before any decrypted data is restored.
 * The password is required for recovery; never stored by this application.
 */
public final class PortableBackup {
    private static final byte[] MAGIC=new byte[]{'C','B','T','B','K','3',0,0};
    private static final int MAX=512*1024*1024;
    private static final String[] AUDIO={"audio","audio_archive"};
    private PortableBackup(){}

    private static final class Keys {
        final SecretKeySpec enc,mac;
        Keys(char[] password,byte[] salt)throws Exception{
            PBEKeySpec spec=new PBEKeySpec(password,salt,210000,512);
            byte[] derived=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            spec.clearPassword();
            enc=new SecretKeySpec(Arrays.copyOfRange(derived,0,32),"AES");
            mac=new SecretKeySpec(Arrays.copyOfRange(derived,32,64),"HmacSHA256");
            Arrays.fill(derived,(byte)0);
        }
    }
    private static class MacOutput extends FilterOutputStream {
        final Mac mac;
        MacOutput(OutputStream o,Mac m){super(o);mac=m;}
        @Override public void write(int b)throws IOException{out.write(b);mac.update((byte)b);}
        @Override public void write(byte[] b,int off,int len)throws IOException{
            out.write(b,off,len);mac.update(b,off,len);
        }
        @Override public void close()throws IOException{flush();} // outer stream owns lifetime
    }
    public static void export(Context ctx,JSONObject doc,char[] password,OutputStream out)throws Exception {
        if(password.length<8)throw new IOException("Backup password requires at least 8 characters.");
        byte[] salt=new byte[16],iv=new byte[16];
        SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(iv);
        Keys keys=new Keys(password,salt);
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(keys.mac);
        out.write(MAGIC);out.write(salt);out.write(iv);
        mac.update(MAGIC);mac.update(salt);mac.update(iv);
        Cipher cipher=Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,keys.enc,new IvParameterSpec(iv));
        MacOutput macOut=new MacOutput(out,mac);
        // Only ZIP data is encrypted, no plaintext temp file on disk.
        CipherOutputStream encrypted=new CipherOutputStream(macOut,cipher);
        ZipOutputStream zip=new ZipOutputStream(encrypted);
        zip.setLevel(Deflater.DEFAULT_COMPRESSION);
        JSONObject safe=new JSONObject(doc.toString());
        // API keys are included only within the password-encrypted backup.
        addBytes(zip,"journal.json",safe.toString().getBytes(StandardCharsets.UTF_8));
        SharedPreferences options=ctx.getSharedPreferences("options",0);
        JSONObject settings=new JSONObject();
        for(Map.Entry<String,?> pair:options.getAll().entrySet()){
            Object val=pair.getValue();
            if(val instanceof Boolean||val instanceof Integer||val instanceof Long||val instanceof String)
                settings.put(pair.getKey(),val);
        }
        addBytes(zip,"options.json",settings.toString().getBytes(StandardCharsets.UTF_8));
        for(String dir:AUDIO){
            File parent=new File(ctx.getFilesDir(),dir);
            File[] children=parent.listFiles();
            if(children==null)continue;
            for(File file:children){
                if(!file.isFile()||!file.getName().matches("[a-zA-Z0-9_.-]+\\.m4a"))continue;
                if(file.length()>MAX)throw new IOException("Audio file exceeds backup limit.");
                ZipEntry entry=new ZipEntry(dir+"/"+file.getName());
                zip.putNextEntry(entry);
                try(InputStream input=new BufferedInputStream(new FileInputStream(file))){
                    transfer(input,zip,MAX);
                }
                zip.closeEntry();
            }
        }
        zip.finish();zip.flush();encrypted.flush();
        // Closing encrypted stream would close SAF output before adding authentication tag.
        // AES CTR is length-preserving; doFinal produces no extra bytes.
        byte[] finalBlock=cipher.doFinal();
        if(finalBlock.length!=0)throw new IOException("Unexpected cipher final data");
        out.write(mac.doFinal());out.flush();
    }
    private static void addBytes(ZipOutputStream zip,String name,byte[] bytes)throws IOException{
        zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();
    }
    private static long transfer(InputStream input,OutputStream output,long max)throws IOException {
        byte[] buf=new byte[32768];int n;long total=0;
        while((n=input.read(buf))!=-1){
            total+=n;if(total>max)throw new IOException("Backup size exceeds safe limit");
            output.write(buf,0,n);
        }
        return total;
    }
    public static JSONObject restore(Context ctx,char[] password,InputStream source)throws Exception {
        if(password.length<8)throw new IOException("Password must be at least 8 characters.");
        File encrypted=File.createTempFile("cbt-backup-",".enc",ctx.getCacheDir());
        File stage=new File(ctx.getCacheDir(),"cbt-restore-"+UUID.randomUUID());
        File rollback=new File(ctx.getCacheDir(),"cbt-rollback-"+UUID.randomUUID());
        try{
            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(encrypted))){
                transfer(source,out,MAX);
            }
            long size=encrypted.length();
            if(size<MAGIC.length+16+16+32||size>MAX)
                throw new IOException("Invalid backup size");
            byte[] header=new byte[40],expectedMac=new byte[32];
            try(RandomAccessFile file=new RandomAccessFile(encrypted,"r")){
                file.readFully(header);
                file.seek(size-32);file.readFully(expectedMac);
            }
            if(!Arrays.equals(MAGIC,Arrays.copyOfRange(header,0,8)))
                throw new IOException("This is not a CBTBK3 backup");
            Keys keys=new Keys(password,Arrays.copyOfRange(header,8,24));
            Mac checker=Mac.getInstance("HmacSHA256");checker.init(keys.mac);
            try(InputStream in=new BufferedInputStream(new FileInputStream(encrypted))){
                long remaining=size-32;byte[] b=new byte[32768];
                while(remaining>0){
                    int n=in.read(b,0,(int)Math.min(b.length,remaining));
                    if(n<0)throw new EOFException("Truncated backup");
                    checker.update(b,0,n);remaining-=n;
                }
            }
            if(!MessageDigest.isEqual(expectedMac,checker.doFinal()))
                throw new IOException("Wrong password or modified backup file");
            if(!stage.mkdir())throw new IOException("Cannot create restore staging area");
            Cipher cipher=Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,keys.enc,
                new IvParameterSpec(Arrays.copyOfRange(header,24,40)));
            JSONObject restored=null,opts=new JSONObject();
            try(InputStream raw=new BufferedInputStream(new FileInputStream(encrypted))){
                long skip=40;while(skip>0)skip-=raw.skip(skip);
                InputStream bounded=new FilterInputStream(raw){
                    long left=size-40-32;
                    @Override public int read()throws IOException{
                        if(left<=0)return -1;
                        int value=super.read();if(value>=0)left--;return value;
                    }
                    @Override public int read(byte[] b,int off,int len)throws IOException{
                        if(left<=0)return -1;
                        int n=super.read(b,off,(int)Math.min(left,len));
                        if(n>0)left-=n;return n;
                    }
                };
                try(ZipInputStream zip=new ZipInputStream(new CipherInputStream(bounded,cipher))){
                    ZipEntry entry;int count=0;long total=0;
                    while((entry=zip.getNextEntry())!=null){
                        if(++count>3000)throw new IOException("Too many archive entries");
                        String name=entry.getName();
                        if(entry.isDirectory()||name.contains("..")||name.startsWith("/")
                            ||name.contains("\\")||name.indexOf(':')>=0)
                            throw new IOException("Unsafe backup path");
                        ByteArrayOutputStream memory=null;
                        OutputStream dest;
                        if("journal.json".equals(name)||"options.json".equals(name)){
                            memory=new ByteArrayOutputStream();dest=memory;
                        }else{
                            String[] path=name.split("/");
                            if(path.length!=2||!Arrays.asList(AUDIO).contains(path[0])
                                ||!path[1].matches("[a-zA-Z0-9_.-]+\\.m4a"))
                                throw new IOException("Unexpected backup entry");
                            File folder=new File(stage,path[0]);if(!folder.exists()&&!folder.mkdir())
                                throw new IOException("Cannot prepare audio folder");
                            dest=new BufferedOutputStream(new FileOutputStream(new File(folder,path[1])));
                        }
                        byte[] b=new byte[32768];int n;
                        while((n=zip.read(b))!=-1){
                            total+=n;
                            if(total>MAX)throw new IOException("Expanded backup too large");
                            dest.write(b,0,n);
                        }
                        dest.flush();if(memory==null)dest.close();
                        else if("journal.json".equals(name)){
                            if(restored!=null)throw new IOException("Duplicate journal in backup");
                            restored=new JSONObject(memory.toString("UTF-8"));
                        }else{
                            opts=new JSONObject(memory.toString("UTF-8"));
                        }
                        zip.closeEntry();
                    }
                }
            }
            if(restored==null||!restored.has("sessions")&&!restored.has("archive"))
                throw new IOException("Backup is missing diary content");
            JSONObject previous=SecureStore.read(ctx);
            Map<String,?> previousOptions=ctx.getSharedPreferences("options",0).getAll();
            if(!rollback.mkdir())throw new IOException("Cannot prepare recovery snapshot");
            try{
                for(String dir:AUDIO){
                    File existing=new File(ctx.getFilesDir(),dir);
                    if(existing.exists()&&!existing.renameTo(new File(rollback,dir)))
                        throw new IOException("Cannot protect existing audio");
                }
                for(String dir:AUDIO){
                    File sourceDir=new File(stage,dir);
                    if(sourceDir.exists()&&!sourceDir.renameTo(new File(ctx.getFilesDir(),dir)))
                        throw new IOException("Cannot install recovered audio");
                }
                SecureStore.write(ctx,restored);
                SharedPreferences.Editor edit=ctx.getSharedPreferences("options",0).edit().clear();
                Iterator<String> names=opts.keys();
                while(names.hasNext()){
                    String key=names.next();Object val=opts.opt(key);
                    if(val instanceof Boolean)edit.putBoolean(key,(Boolean)val);
                    else if(val instanceof Integer)edit.putInt(key,(Integer)val);
                    else if(val instanceof Long)edit.putLong(key,(Long)val);
                    else if(val instanceof String)edit.putString(key,(String)val);
                }
                if(!edit.commit())throw new IOException("Cannot restore settings");
            }catch(Exception error){
                try{SecureStore.write(ctx,previous);}catch(Exception ignored){}
                for(String dir:AUDIO){
                    deleteTree(new File(ctx.getFilesDir(),dir));
                    File saved=new File(rollback,dir);
                    if(saved.exists())saved.renameTo(new File(ctx.getFilesDir(),dir));
                }
                SharedPreferences.Editor old=ctx.getSharedPreferences("options",0).edit().clear();
                for(Map.Entry<String,?> item:previousOptions.entrySet()){
                    Object val=item.getValue();
                    if(val instanceof Boolean)old.putBoolean(item.getKey(),(Boolean)val);
                    else if(val instanceof Integer)old.putInt(item.getKey(),(Integer)val);
                    else if(val instanceof Long)old.putLong(item.getKey(),(Long)val);
                    else if(val instanceof String)old.putString(item.getKey(),(String)val);
                }
                old.commit();
                throw error;
            }
            ReminderReceiver.schedule(ctx);
            return restored;
        }finally{
            encrypted.delete();deleteTree(stage);deleteTree(rollback);
        }
    }
    private static void deleteTree(File f){
        if(!f.exists())return;
        File[] children=f.listFiles();
        if(children!=null)for(File c:children)deleteTree(c);
        f.delete();
    }
}
