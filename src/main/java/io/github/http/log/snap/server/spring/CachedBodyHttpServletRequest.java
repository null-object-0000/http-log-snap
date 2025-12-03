package io.github.http.log.snap.server.spring;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 可缓存请求体的 HttpServletRequest 包装器
 * 允许多次读取请求体内容
 *
 * @author http-logging
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private byte[] cachedBody;
    private boolean bodyRead = false;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
    }

    /**
     * 获取缓存的请求体字节数组
     */
    public byte[] getCachedBody() throws IOException {
        if (!bodyRead) {
            this.cachedBody = getRequest().getInputStream().readAllBytes();
            this.bodyRead = true;
        }
        return cachedBody;
    }

    /**
     * 获取缓存的请求体字符串
     */
    public String getCachedBodyString() throws IOException {
        return getCachedBodyString(getCharacterEncodingOrDefault());
    }

    /**
     * 获取缓存的请求体字符串（指定编码）
     */
    public String getCachedBodyString(Charset charset) throws IOException {
        return new String(getCachedBody(), charset);
    }

    /**
     * 获取请求体字节数
     */
    public int getCachedBodyLength() throws IOException {
        return getCachedBody().length;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        // 确保请求体已被缓存
        getCachedBody();
        return new CachedBodyServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncodingOrDefault()));
    }

    private Charset getCharacterEncodingOrDefault() {
        String encoding = getCharacterEncoding();
        if (encoding != null) {
            try {
                return Charset.forName(encoding);
            } catch (Exception ignored) {
            }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * 基于缓存字节数组的 ServletInputStream 实现
     */
    private static class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream inputStream;

        public CachedBodyServletInputStream(byte[] cachedBody) {
            this.inputStream = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException("Async read not supported");
        }

        @Override
        public int read() {
            return inputStream.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return inputStream.read(b, off, len);
        }

        @Override
        public int available() {
            return inputStream.available();
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }
}

