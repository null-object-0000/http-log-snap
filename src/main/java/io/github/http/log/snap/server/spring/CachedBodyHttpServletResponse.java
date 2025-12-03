package io.github.http.log.snap.server.spring;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 可缓存响应体的 HttpServletResponse 包装器
 * 允许捕获响应体内容用于日志记录
 *
 * @author http-logging
 */
public class CachedBodyHttpServletResponse extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream cachedContent = new ByteArrayOutputStream(1024);
    private ServletOutputStream outputStream;
    private PrintWriter writer;

    public CachedBodyHttpServletResponse(HttpServletResponse response) {
        super(response);
    }

    /**
     * 获取缓存的响应体字节数组
     */
    public byte[] getCachedBody() {
        return cachedContent.toByteArray();
    }

    /**
     * 获取缓存的响应体字符串
     */
    public String getCachedBodyString() {
        return getCachedBodyString(getCharacterEncodingOrDefault());
    }

    /**
     * 获取缓存的响应体字符串（指定编码）
     */
    public String getCachedBodyString(Charset charset) {
        return cachedContent.toString(charset);
    }

    /**
     * 获取响应体字节数
     */
    public int getCachedBodyLength() {
        return cachedContent.size();
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (outputStream == null) {
            outputStream = new CachedBodyServletOutputStream(getResponse().getOutputStream(), cachedContent);
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), getCharacterEncodingOrDefault()), true);
        }
        return writer;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) {
            writer.flush();
        } else if (outputStream != null) {
            outputStream.flush();
        }
        super.flushBuffer();
    }

    /**
     * 将缓存的内容写入实际响应（如果尚未写入）
     */
    public void copyBodyToResponse() throws IOException {
        if (cachedContent.size() > 0) {
            getResponse().getOutputStream().write(cachedContent.toByteArray());
            getResponse().getOutputStream().flush();
        }
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
     * 同时写入缓存和实际响应的 ServletOutputStream 实现
     */
    private static class CachedBodyServletOutputStream extends ServletOutputStream {

        private final ServletOutputStream outputStream;
        private final ByteArrayOutputStream cache;

        public CachedBodyServletOutputStream(ServletOutputStream outputStream, ByteArrayOutputStream cache) {
            this.outputStream = outputStream;
            this.cache = cache;
        }

        @Override
        public boolean isReady() {
            return outputStream.isReady();
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            outputStream.setWriteListener(listener);
        }

        @Override
        public void write(int b) throws IOException {
            cache.write(b);
            outputStream.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            cache.write(b);
            outputStream.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            cache.write(b, off, len);
            outputStream.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            outputStream.flush();
        }

        @Override
        public void close() throws IOException {
            outputStream.close();
        }
    }
}

