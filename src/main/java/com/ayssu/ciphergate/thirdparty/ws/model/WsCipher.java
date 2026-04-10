package com.ayssu.ciphergate.thirdparty.ws.model;

import lombok.Data;

/**
 * AES-GCM encrypted payload wrapper.
 */
@Data
public class WsCipher {
    private String alg;   // e.g. "AES-256-GCM"
    private String iv;    // base64
    private String data;  // base64 ciphertext
    private String tag;   // base64 tag (optional; can be embedded in data)
}

