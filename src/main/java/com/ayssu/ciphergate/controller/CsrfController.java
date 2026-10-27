package com.ayssu.ciphergate.controller;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Same-origin clients obtain a session-bound, masked token before a mutation. */
@RestController
public class CsrfController {
    @GetMapping("/api/csrf")
    public ResponseEntity<Map<String, String>> token(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
    }
}
