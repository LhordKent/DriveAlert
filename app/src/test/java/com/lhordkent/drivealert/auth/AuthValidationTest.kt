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
    fun signUpPasswordRequiresLengthBothCasesAndFirebaseSpecialCharacter() {
        assertEquals("Password is required", AuthValidation.signUpPasswordError(""))
        for (password in listOf("Short!", "alllower!", "ALLUPPER!", "NoSpecial123", "Space Only", "EmojiOnly🔒")) {
            assertEquals(password, AuthValidation.SIGN_UP_PASSWORD_REQUIREMENTS, AuthValidation.signUpPasswordError(password))
        }
        assertNull(AuthValidation.signUpPasswordError("SafePass!"))
        assertNull(AuthValidation.signUpPasswordError("Safe-drive"))
        assertNull(AuthValidation.signUpPasswordError("SafePass`"))
        assertNull(AuthValidation.signInPasswordError("safe-drive"))
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
