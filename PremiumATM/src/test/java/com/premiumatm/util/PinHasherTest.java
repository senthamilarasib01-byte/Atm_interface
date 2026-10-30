package com.premiumatm.util;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PinHasherTest {
    @Test void verifiesCorrectPinAndRejectsWrongPin() {
        PinHasher.Hash h=PinHasher.hash("1234");
        assertTrue(PinHasher.verify("1234",h.hash(),h.salt()));
        assertFalse(PinHasher.verify("9999",h.hash(),h.salt()));
        assertNotEquals("1234",h.hash());
    }
}
