package com.ayssu.ciphergate.thirdparty.auth;

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

public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    public CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
        super(request);
        this.body = body == null ? new byte[0] : body;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream bais = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return bais.available() <= 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // not async
            }

            @Override
            public int read() {
                return bais.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        Charset cs = StandardCharsets.UTF_8;
        String enc = getCharacterEncoding();
        if (enc != null) {
            try {
                cs = Charset.forName(enc);
            } catch (Exception ignored) {
            }
        }
        return new BufferedReader(new InputStreamReader(getInputStream(), cs));
    }
}

