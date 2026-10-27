package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.entity.QuotaProduct;
import com.ayssu.ciphergate.service.QuotaProductService;
import com.ayssu.ciphergate.service.UserMembershipService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PaymentInputValidationTest {
    PaymentOrderServiceImpl orders = new PaymentOrderServiceImpl();
    QuotaProductService products = mock(QuotaProductService.class);
    UserMembershipService members = mock(UserMembershipService.class);
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(orders,"quotaProductService", products);
        ReflectionTestUtils.setField(orders,"userMembershipService", members);
    }
    @Test void zeroNegativeAndExcessiveQuantityCannotDeductOrUpgrade() {
        for (int quantity : new int[]{0,-1,Integer.MIN_VALUE,10001,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,()->orders.createOrder(1L,2L,quantity));
        }
        verifyNoInteractions(products, members);
    }
    @Test void membershipCannotBuyMultipleAndAmountCannotOverflowOrBeNonpositive() {
        var p = new QuotaProduct();p.setStatus(1);p.setProductType("MEMBERSHIP");p.setPrice(10L);
        when(products.getById(2L)).thenReturn(p);
        assertThrows(IllegalArgumentException.class,()->orders.createOrder(1L,2L,2));
        p.setProductType("LICENSE_QUOTA");p.setPrice(Long.MAX_VALUE);
        assertThrows(ArithmeticException.class,()->orders.createOrder(1L,2L,2));
        for (Long price : new Long[]{null,0L,-10L}) {
            p.setPrice(price);assertThrows(IllegalArgumentException.class,()->orders.createOrder(1L,2L,1));
        }
        verifyNoInteractions(members);
    }
    @Test void balanceAndPendingOrderRejectNonpositiveAmountsBeforePersistence() {
        var service = new UserMembershipServiceImpl();
        for (Long amount : new Long[]{null,0L,-1L}) {
            assertThrows(IllegalArgumentException.class,()->service.grantBalance(1L,amount,null,"test"));
            assertThrows(IllegalArgumentException.class,()->service.deductBalance(1L,amount,null,"test"));
            assertThrows(IllegalArgumentException.class,()->orders.createPendingOrder(1L,"test",amount,"order","RECHARGE"));
        }
    }
}
