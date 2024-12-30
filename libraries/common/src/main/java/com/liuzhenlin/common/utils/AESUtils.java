/*
 * Created on 2021-12-27 5:46:23 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Base64;

import androidx.annotation.NonNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES对称加密算法，加解密工具类
 */
public class AESUtils {

    private static final String TAG = "AESUtils";

    /**
     * 加密算法
     */
    private static final String KEY_ALGORITHM = "AES";

    // 密钥长度范围
    private static final int MIN_KEY_LENGTH = 1 << 4; // 2 ^ 4
    private static final int MAX_KEY_LENGTH = 1 << 5; // 2 ^ 5

    /**
     * 默认的秘钥字符填充
     */
    private static final char KEY_PADDING = '0';

    /**
     * 字符编码
     */
    @SuppressLint("NewApi")
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    /**
     * 加解密算法/工作模式/填充方式
     */
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS7Padding";

    private AESUtils() {
    }

    /**
     * AES加密
     *
     * @param data 待加密内容
     * @return 返回Base64转码后的加密数据
     */
    @NonNull
    public static String encrypt(@NonNull Context context, @NonNull String data)
            throws GeneralSecurityException {
        return encrypt(context, data, nGetDefaultSecretKey(context));
    }

    /**
     * AES加密
     *
     * @param data      待加密内容
     * @param secretKey 秘钥，建议的长度范围 [{@value MIN_KEY_LENGTH}, {@value MAX_KEY_LENGTH}]
     * @return 返回Base64转码后的加密数据
     */
    @NonNull
    public static String encrypt(
            @NonNull Context context, @NonNull String data, @NonNull String secretKey)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, getKey(secretKey),
                new IvParameterSpec(nGetInitializationVector(context).getBytes(CHARSET)));
        byte[] encryptedData = cipher.doFinal(data.getBytes(CHARSET));
        return new String(base64Encode(encryptedData), CHARSET);
    }

    /**
     * AES解密
     *
     * @param base64Data 加密了的密文，Base64字符串
     */
    @NonNull
    public static String decrypt(@NonNull Context context, @NonNull String base64Data)
            throws GeneralSecurityException {
        return decrypt(context, base64Data, nGetDefaultSecretKey(context));
    }

    /**
     * AES解密
     *
     * @param base64Data 加密了的密文，Base64字符串
     * @param secretKey  解密的密钥，建议的长度范围 [{@value MIN_KEY_LENGTH}, {@value MAX_KEY_LENGTH}]
     */
    @NonNull
    public static String decrypt(
            @NonNull Context context, @NonNull String base64Data, @NonNull String secretKey)
            throws GeneralSecurityException {
        byte[] encryptedData = base64Decode(base64Data);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, getKey(secretKey),
                new IvParameterSpec(nGetInitializationVector(context).getBytes(CHARSET)));
        byte[] data = cipher.doFinal(encryptedData);
        return new String(data, CHARSET);
    }

    private static SecretKeySpec getKey(String secretKey) {
        secretKey = normalizedKey(secretKey, KEY_PADDING);
        return new SecretKeySpec(secretKey.getBytes(CHARSET), KEY_ALGORITHM);
    }

    @SuppressWarnings("SameParameterValue")
    private static String normalizedKey(String secretKey, char keyPadding) {
        int keyLen = secretKey.length();
        int keyLength = keyLengthFor(keyLen);
        if (keyLen < keyLength) {
            StringBuilder keyBuilder = new StringBuilder(secretKey);
            for (int i = keyLength - keyLen; i > 0; i--) {
                keyBuilder.append(keyPadding);
            }
            return keyBuilder.toString();
        } else if (keyLen > keyLength) {
            return secretKey.substring(0, keyLength);
        }
        return secretKey;
    }

    private static int keyLengthFor(int len) {
        int n = len - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0)
                ? 1
                : (n <= MIN_KEY_LENGTH) ?
                        MIN_KEY_LENGTH : (n >= MAX_KEY_LENGTH) ? MAX_KEY_LENGTH : n + 1;
    }

    private static byte[] base64Decode(String data) {
        return Base64.decode(data, Base64.NO_WRAP);
    }

    private static byte[] base64Encode(byte[] data) {
        return Base64.encode(data, Base64.NO_WRAP);
    }

    private static native String nGetDefaultSecretKey(Context context);
    private static native String nGetInitializationVector(Context context);
}