package com.lhordkent.drivealert.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {
    @Test
    fun email_requiresValueAndValidFormat() {
        assertEquals("Email is required", AuthValidation.emailError(""))
        assertEquals("Enter a valid email address", AuthValidation.emailError("lhord@drivealert"))
        assertNull(AuthValidation.emailError("lhord@drivealert.app"))
    }

    @Test
    fun requiredFieldsRejectBlankValues() {
        assertEquals("First name is required", AuthValidation.requiredError("  ", "First name"))
        assertNull(AuthValidation.requiredError("Lhord", "First name"))
    }

    @Test
    fun signUpPasswordRequiresEightCharacters() {
        assertEquals("Password is required", AuthValidation.signUpPasswordError(""))
        assertEquals("Use at least 8 characters", AuthValidation.signUpPasswordError("1234567"))
        assertNull(AuthValidation.signUpPasswordError("12345678"))
    }

    @Test
    fun confirmationMustMatchPassword() {
        assertEquals("Confirm your password", AuthValidation.confirmationError("password", ""))
        assertEquals("Passwords do not match", AuthValidation.confirmationError("password", "different"))
        assertNull(AuthValidation.confirmationError("password", "password"))
    }

    @Test
    fun phoneNumberIsRequired() {
        assertEquals("Phone number is required", AuthValidation.phoneError(""))
        assertEquals("Phone number is required", AuthValidation.phoneError("   "))
    }

    @Test
    fun phoneNumberRequiresExactlyElevenAsciiDigits() {
        assertNull(AuthValidation.phoneError("09171234567"))
        assertNull(AuthValidation.phoneError(" 09171234567 "))
        assertNull(AuthValidation.phoneError("08171234567"))
        assertNull(AuthValidation.phoneError("12345678901"))
        for (number in listOf("0917123456", "091712345678", "+639171234567",
            "0917 1234567", "0917-1234567", "call-me", "09abcdefghi", "09١٢٣٤٥٦٧٨٩")) {
            assertEquals(number, "Enter exactly 11 digits", AuthValidation.phoneError(number))
        }
    }
}
