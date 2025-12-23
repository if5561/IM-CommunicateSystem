package com.lld.im.common.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * @description:
 * @author: lld
 * @version: 1.0
 */
public class Base64URL {

    public static byte[] base64EncodeUrl(byte[] input) {
        String base64 = Base64.getEncoder().encodeToString(input);
        byte[] result = base64.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < result.length; ++i) {
            switch (result[i]) {
                case '+':
                    result[i] = '*';
                    break;
                case '/':
                    result[i] = '-';
                    break;
                case '=':
                    result[i] = '_';
                    break;
                default:
                    break;
            }
        }
        return result;
    }

    public static byte[] base64EncodeUrlNotReplace(byte[] input) {
        String base64 = Base64.getEncoder().encodeToString(input);
        byte[] result = base64.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < result.length; ++i) {
            switch (result[i]) {
                case '+':
                    result[i] = '*';
                    break;
                case '/':
                    result[i] = '-';
                    break;
                case '=':
                    result[i] = '_';
                    break;
                default:
                    break;
            }
        }
        return result;
    }

    public static byte[] base64DecodeUrlNotReplace(byte[] input) {
        String replaced = new String(input, StandardCharsets.UTF_8);
        replaced = replaced.replace('*', '+').replace('-', '/').replace('_', '=');
        return Base64.getDecoder().decode(replaced);
    }

    public static byte[] base64DecodeUrl(byte[] input) {
        byte[] base64 = input.clone();
        for (int i = 0; i < base64.length; ++i) {
            switch (base64[i]) {
                case '*':
                    base64[i] = '+';
                    break;
                case '-':
                    base64[i] = '/';
                    break;
                case '_':
                    base64[i] = '=';
                    break;
                default:
                    break;
            }
        }
        String base64Str = new String(base64, StandardCharsets.UTF_8);
        return Base64.getDecoder().decode(base64Str);
    }
}