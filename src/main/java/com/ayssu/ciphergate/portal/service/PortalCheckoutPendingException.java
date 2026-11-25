package com.ayssu.ciphergate.portal.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Keep the order and reservation when the gateway outcome is uncertain. */
public class PortalCheckoutPendingException extends ResponseStatusException {
    public PortalCheckoutPendingException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "付款地址暂时无法取得，订单已保留，请先查看订单记录，避免重复下单");
    }
}
