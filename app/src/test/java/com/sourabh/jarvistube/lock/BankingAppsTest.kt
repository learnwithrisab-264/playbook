package com.sourabh.jarvistube.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankingAppsTest {
    @Test
    fun knownAndNamedBankingApps() {
        assertTrue(isBankingApp("net.one97.paytm"))
        assertTrue(isBankingApp("com.phonepe.app"))
        assertTrue(isBankingApp("com.example.mybank.mobile"))
        assertTrue(isBankingApp("in.someone.upiapp"))
        assertFalse(isBankingApp("com.whatsapp"))
        assertFalse(isBankingApp("com.sourabh.jarvistube"))
    }
}
