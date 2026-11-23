package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.config.EpayConfig;
import com.ayssu.ciphergate.entity.PaymentOrder;
import com.ayssu.ciphergate.util.SkPaymentSignature;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkPaymentCallbackTest {
    @Test void separatesReturnAndNotifyAndChecksAmountBeforeGrantingCredit() {
        EpayConfig config=mock(EpayConfig.class);
        when(config.getEpayPid()).thenReturn("123");when(config.getEpayKey()).thenReturn("test-secret");
        PaymentOrderService orders=mock(PaymentOrderService.class);
        EpayService service=new EpayService(config,orders,mock(UserMembershipService.class));
        PaymentOrder order=new PaymentOrder();order.setTotalAmount(101L);
        when(orders.getByOrderNo("order-1")).thenReturn(order);
        Map<String,String> p=new TreeMap<>(Map.of("pid","123","money","1.01","name","中文 &=+","out_trade_no","order-1","trade_no","trade-1","type","alipay","trade_status","TRADE_SUCCESS"));
        SkPaymentSignature.seal(p,"test-secret","payment.notify");
        assertTrue(service.verifyNotifySign(p));assertFalse(service.verifyReturnSign(p));
        assertThrows(IllegalArgumentException.class,()->service.handleReturn(p));
        service.handleNotify(p);verify(orders).handlePaymentSuccess("order-1","trade-1");
        p.put("money","2.00");SkPaymentSignature.seal(p,"test-secret","payment.return");
        assertTrue(service.verifyReturnSign(p));assertFalse(service.verifyNotifySign(p));
        assertThrows(IllegalArgumentException.class,()->service.handleReturn(p));
        verify(orders,times(1)).handlePaymentSuccess(anyString(),anyString());
        p.put("pid","other");SkPaymentSignature.seal(p,"test-secret","payment.notify");assertFalse(service.verifyNotifySign(p));
    }
}
