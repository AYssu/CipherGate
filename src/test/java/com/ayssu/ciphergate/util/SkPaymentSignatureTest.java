package com.ayssu.ciphergate.util;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SkPaymentSignatureTest {
 private Map<String,String> request() {
  Map<String,String> p=new HashMap<>();p.put("money","1.00");p.put("name","中文 &=+");p.put("nonce","abcdefghijklmnop");p.put("purpose","payment.create");p.put("sign_type","HMAC-SHA256");p.put("timestamp","1700000000");return p;
 }
 @Test void independentVectorAndEncoding() { var p=request();assertEquals("45c123abfef41d654896fbcfa33e2040dd0409d6b4bf5bbd0cd6960229e6ab24",SkPaymentSignature.sign(p,"test-secret"));assertEquals("money=1.00&name=%E4%B8%AD%E6%96%87%20%26%3D%2B&nonce=abcdefghijklmnop&purpose=payment.create&sign_type=HMAC-SHA256&timestamp=1700000000",SkPaymentSignature.canonical(p)); }
 @Test void rejectsTamperAndLegacy() {var p=request();p.put("sign",SkPaymentSignature.sign(p,"test-secret"));assertTrue(SkPaymentSignature.verify(p,"test-secret","payment.create",1700000000));for(String k:new ArrayList<>(p.keySet())) {var changed=new HashMap<>(p);changed.put(k,p.get(k)+"x");assertFalse(SkPaymentSignature.verify(changed,"test-secret","payment.create",1700000000),k);}assertFalse(SkPaymentSignature.verify(p,"wrong","payment.create",1700000000));assertFalse(SkPaymentSignature.verify(p,"test-secret","payment.notify",1700000000));p.put("sign_type","MD5");assertFalse(SkPaymentSignature.verify(p,"test-secret","payment.create",1700000000));}
 @Test void timeBoundsAndMalformedInputs() {var p=request();p.put("sign",SkPaymentSignature.sign(p,"test-secret"));assertTrue(SkPaymentSignature.verify(p,"test-secret","payment.create",1700000300));assertTrue(SkPaymentSignature.verify(p,"test-secret","payment.create",1699999700));assertFalse(SkPaymentSignature.verify(p,"test-secret","payment.create",1700000301));assertFalse(SkPaymentSignature.verify(p,"test-secret","payment.create",1699999699));p.put("timestamp","9999999999999999999999999");assertFalse(SkPaymentSignature.verify(p,"test-secret","payment.create",1700000000));}
 @Test void nullOmittedEmptySignedAndDelimiterUnambiguous() {var p=request();String original=SkPaymentSignature.sign(p,"test-secret");p.put("optional",null);assertEquals(original,SkPaymentSignature.sign(p,"test-secret"));p.put("optional","");assertNotEquals(original,SkPaymentSignature.sign(p,"test-secret"));assertNotEquals(SkPaymentSignature.canonical(Map.of("a","x&b=y")),SkPaymentSignature.canonical(Map.of("a","x","b","y")));}
}
