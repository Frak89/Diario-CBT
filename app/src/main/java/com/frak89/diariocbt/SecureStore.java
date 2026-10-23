package com.frak89.diariocbt;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;

public final class SecureStore {
    private static final String ALIAS = "cbt_diary_key_v1";
    private SecureStore() {}
    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return kg.generateKey();
    }
    public static synchronized JSONObject read(Context ctx) {
        String v=ctx.getSharedPreferences("private",0).getString("data", "");
        if (v.isEmpty()) return new JSONObject();
        try {
            byte[] bytes=Base64.decode(v,Base64.NO_WRAP);
            if (bytes.length < 12 + 16) throw new IllegalStateException("Invalid encrypted journal format");
            byte[] iv=new byte[12];
            System.arraycopy(bytes,0,iv,0,12);
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            return new JSONObject(new String(c.doFinal(bytes,12,bytes.length-12), java.nio.charset.StandardCharsets.UTF_8));
        } catch(Exception ex) { throw new IllegalStateException("Unable to decrypt journal; data preserved.",ex); }
    }
    public static synchronized void write(Context ctx, JSONObject data) {
        try {
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            // AndroidKeyStore requires it to choose a fresh IV during encryption.
            // Supplying a caller-generated IV throws InvalidAlgorithmParameterException.
            c.init(Cipher.ENCRYPT_MODE,key());
            byte[] iv=c.getIV();
            if(iv == null || iv.length != 12) throw new IllegalStateException("Unexpected GCM IV length");
            byte[] raw=c.doFinal(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] payload=new byte[iv.length+raw.length];
            System.arraycopy(iv,0,payload,0,iv.length);
            System.arraycopy(raw,0,payload,iv.length,raw.length);
            boolean ok=ctx.getSharedPreferences("private",0).edit().putString("data",Base64.encodeToString(payload,Base64.NO_WRAP)).commit();
            if (!ok) throw new IllegalStateException("Storage write unsuccessful");
        } catch(Exception ex) { throw new IllegalStateException("Unable to write encrypted journal.",ex); }
    }
}
