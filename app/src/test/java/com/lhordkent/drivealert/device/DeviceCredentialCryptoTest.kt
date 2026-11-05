package com.lhordkent.drivealert.device

import com.lhordkent.drivealert.data.device.DeviceIdentity
import org.junit.Assert.*
import org.junit.Test

class DeviceCredentialCryptoTest {
    @Test fun canonicalIdentityUsesFullAddressAndRejectsRoutingHints() {
        assertEquals("AABBCCDDEEFF", DeviceIdentity.canonical("aa:bb:cc:dd:ee:ff"))
        assertNull(DeviceIdentity.canonical("drivealert-eeff"))
        assertNull(DeviceIdentity.canonical("192.168.1.20"))
        assertNull(DeviceIdentity.canonical("GG1122334455"))
    }

    @Test fun certificateAndChallengeMatchIndependentHmacSha256Vectors() {
        val owner = DeviceCredential("AABBCCDDEEFF", "00".repeat(16), "AB".repeat(32), true)
        val driver = DeviceCredentialCrypto.delegate(owner, "driver-a", 1, "operation-1")
        assertEquals("6CFCC864FBBCF4E5AC9C71C71E94BDA3", driver.accountId)
        assertEquals("ACA2D3653C0FB5EDA30E85CC66B791E3CC6530E2D975A1594191FFF4E1B16617", driver.secret)
        assertEquals("0C1C1188443E9A8160E26652D4FA4DFD5098605A9ED75C37260EAB1857E524B1", driver.certificate)
        assertEquals("92E0B0A6C75791F3E7F588556A05DA72AC283E4A73359476C7D60B95208933FC", DeviceCredentialCrypto.proof(driver, "12".repeat(16)))
        assertNotEquals(DeviceCredentialCrypto.proof(driver, "12".repeat(16)), DeviceCredentialCrypto.proof(driver, "34".repeat(16)))
        assertNotEquals(driver.certificate, DeviceCredentialCrypto.delegate(owner, "driver-a", 2, "operation-1").certificate)
        assertNotEquals(driver.secret, DeviceCredentialCrypto.delegate(owner, "driver-b", 1, "operation-1").secret)
    }

    @Test fun aDriverCannotIssueCertificatesAndInvalidNoncesAreRejected() {
        val driver = DeviceCredential("AABBCCDDEEFF", "00".repeat(16), "AB".repeat(32), false, 1)
        assertThrows(IllegalArgumentException::class.java) { DeviceCredentialCrypto.delegate(driver, "other", 1, "op") }
        assertThrows(IllegalArgumentException::class.java) { DeviceCredentialCrypto.proof(driver, "bad-nonce") }
    }
}
