/*
 *     This file is part of Telekram (Telegram MTProto client library)
 *     Copyright (C) 2020 Hackintosh Five
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.hack5.telekram.core.auth

import dev.hack5.telekram.core.crypto.doPBKDF2SHA512Iter100000
import dev.hack5.telekram.core.tl.Account_PasswordObject
import dev.hack5.telekram.core.tl.InputCheckPasswordSRPObject
import dev.hack5.telekram.core.tl.PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPowObject
import dev.hack5.telekram.core.tl.SecurePasswordKdfAlgoPBKDF2HMACSHA512iter100000Object
import org.gciatto.kt.math.BigInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalStdlibApi
class PasswordAuthenticatorTest {
    val random = byteArrayOf(31, 2, -13, 55, -74, 114, -86, 84, -102, -51, -72, 75, -23, 121, -16, 36, 100, -14, -42, -61, -22, 39, -30, 20, -111, 94, -46, -20, 23, 62, 9, -73, -39, 34, -87, -38, -106, -50, -123, 8, -97, 50, 5, -111, -8, 104, 30, 32, -66, -61, 78, -37, 103, 54, -65, -20, 4, -33, -21, -2, 1, -91, 104, 53, -7, 104, -30, 29, -41, 117, 22, -65, 2, 102, 38, 109, 19, -85, -7, 80, -40, 112, -78, 94, -105, 126, 10, -66, -72, 34, 52, -44, -31, 21, 120, 94, -29, -55, -49, -118, -91, 31, -29, 117, 43, 96, -100, -111, -96, 22, -124, 30, 116, 53, -78, 118, -51, 25, -101, -119, -107, 114, 77, 62, 106, -103, -58, 81, -62, -26, 43, -102, -128, -101, 1, -9, -50, -44, 122, -42, -22, -62, 24, 89, -95, -113, -68, 70, 77, -105, -25, 81, -43, 103, 97, 71, -62, 90, 8, 67, -109, -107, 56, 45, -125, 125, 13, 0, 84, -123, -53, 101, 72, -118, 19, 3, 85, -47, -103, 66, -125, 105, -24, -95, 112, -67, 20, 58, 59, 101, -45, 49, -121, -10, 100, -72, 110, 38, 116, -23, -18, 119, 48, 47, 49, -109, -14, 113, 100, 90, -110, 15, 5, -72, 41, -17, -67, -51, -103, 122, 2, 111, 8, -113, -2, -71, -17, -92, 41, 2, -94, -82, 1, 17, 93, 51, -127, -36, 99, 125, -45, 14, 85, -127, -51, 79, 33, -96, 120, -52, -35, -75, 65, 52, 5, -110)
    val serverParams = Account_PasswordObject(
        hasRecovery=true,
        hasSecureValues=false,
        hasPassword=true,
        currentAlgo=PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPowObject(
            salt1=byteArrayOf(-65, -97, 42, 114, 62, 69, -114, -34, -5, -79, -72, -22, 88, 109, 89, -77, 10, -41, 9, 28, 62, -104, 108, 88, 118, -120, 93, 15, 99, -35, 6, -33, -70, -19, -119, -29, -3, 30, -82, 33),
            salt2=byteArrayOf(35, 41, 56, -125, -99, 38, -69, 122, 115, 38, -34, -122, 47, 21, 24, 13),
            g=3,
            p=byteArrayOf(-57, 28, -82, -71, -58, -79, -55, 4, -114, 108, 82, 47, 112, -15, 63, 115, -104, 13, 64, 35, -114, 62, 33, -63, 73, 52, -48, 55, 86, 61, -109, 15, 72, 25, -118, 10, -89, -63, 64, 88, 34, -108, -109, -46, 37, 48, -12, -37, -6, 51, 111, 110, 10, -55, 37, 19, -107, 67, -82, -44, 76, -50, 124, 55, 32, -3, 81, -10, -108, 88, 112, 90, -58, -116, -44, -2, 107, 107, 19, -85, -36, -105, 70, 81, 41, 105, 50, -124, 84, -15, -113, -81, -116, 89, 95, 100, 36, 119, -2, -106, -69, 42, -108, 29, 91, -51, 29, 74, -56, -52, 73, -120, 7, 8, -6, -101, 55, -114, 60, 79, 58, -112, 96, -66, -26, 124, -7, -92, -92, -90, -107, -127, 16, 81, -112, 126, 22, 39, 83, -75, 107, 15, 107, 65, 13, -70, 116, -40, -88, 75, 42, 20, -77, 20, 78, 14, -15, 40, 71, 84, -3, 23, -19, -107, 13, 89, 101, -76, -71, -35, 70, 88, 45, -79, 23, -115, 22, -100, 107, -60, 101, -80, -42, -1, -100, -93, -110, -113, -17, 91, -102, -28, -28, 24, -4, 21, -24, 62, -66, -96, -8, 127, -87, -1, 94, -19, 112, 5, 13, -19, 40, 73, -12, 123, -7, 89, -39, 86, -123, 12, -23, 41, -123, 31, 13, -127, 21, -10, 53, -79, 5, -18, 46, 78, 21, -48, 75, 36, 84, -65, 111, 79, -83, -16, 52, -79, 4, 3, 17, -100, -40, -29, -71, 47, -52, 91)
        ),
        srpB=byteArrayOf(109, 52, -101, 105, 126, 64, -16, 69, 123, 60, -82, 38, 16, -52, -78, -29, 49, -24, -31, -52, -52, -71, -69, -88, 85, 2, -107, 55, 115, -113, -13, -99, 11, -76, 43, -122, -9, 103, 107, 82, -60, -93, -118, -79, -124, 122, 75, 117, -90, -12, 77, 71, -46, 9, -39, -41, 56, -25, -52, -69, 114, 104, 45, 2, -29, -111, 96, -18, -14, -28, 51, -75, 35, -124, -73, 11, 106, -63, -109, 62, 54, 20, 124, -55, 49, -76, 77, 50, -112, 43, 107, -41, 83, 5, 8, -68, 6, -28, 48, 11, -65, 4, 113, 47, 53, -119, -49, 103, 26, -37, -5, 10, 47, 117, 30, -58, -53, -17, 75, -98, -9, 76, 40, 85, 55, -118, 20, 57, 6, 61, 44, -2, -66, -34, -102, -23, -57, -2, 58, 127, -116, 6, 96, 124, -12, -38, 100, 54, 39, -19, -91, 59, 70, -2, -23, 113, -85, -75, 110, 31, 43, -26, -106, -39, 49, 97, 62, -95, -35, 50, 85, 12, -17, 14, -91, -41, 15, -53, -28, 51, 21, -24, 6, -35, 22, -101, 38, -113, -103, 26, -81, 112, 54, 19, 76, 98, -27, -120, -68, -85, -95, 125, -31, -14, 83, -77, 8, -57, 14, -86, -57, 9, 42, 57, 16, 75, 96, -127, 18, -94, -84, 81, -15, 89, -9, -119, -71, 13, -12, -16, 20, 54, 2, 81, 75, -40, -125, 77, 66, -109, 30, 47, 120, 97, 113, -124, -13, -82, -73, -87, 22, -63, 81, 23, -36, 125),
        srpId=-9035356578752577569,
        hint="password1",
        emailUnconfirmedPattern=null,
        newAlgo=PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPowObject(
            salt1=byteArrayOf(-65, -97, 42, 114, 62, 69, -114, -34),
            salt2=byteArrayOf(35, 41, 56, -125, -99, 38, -69, 122, 115, 38, -34, -122, 47, 21, 24, 13),
            g=3,
            p=byteArrayOf(-57, 28, -82, -71, -58, -79, -55, 4, -114, 108, 82, 47, 112, -15, 63, 115, -104, 13, 64, 35, -114, 62, 33, -63, 73, 52, -48, 55, 86, 61, -109, 15, 72, 25, -118, 10, -89, -63, 64, 88, 34, -108, -109, -46, 37, 48, -12, -37, -6, 51, 111, 110, 10, -55, 37, 19, -107, 67, -82, -44, 76, -50, 124, 55, 32, -3, 81, -10, -108, 88, 112, 90, -58, -116, -44, -2, 107, 107, 19, -85, -36, -105, 70, 81, 41, 105, 50, -124, 84, -15, -113, -81, -116, 89, 95, 100, 36, 119, -2, -106, -69, 42, -108, 29, 91, -51, 29, 74, -56, -52, 73, -120, 7, 8, -6, -101, 55, -114, 60, 79, 58, -112, 96, -66, -26, 124, -7, -92, -92, -90, -107, -127, 16, 81, -112, 126, 22, 39, 83, -75, 107, 15, 107, 65, 13, -70, 116, -40, -88, 75, 42, 20, -77, 20, 78, 14, -15, 40, 71, 84, -3, 23, -19, -107, 13, 89, 101, -76, -71, -35, 70, 88, 45, -79, 23, -115, 22, -100, 107, -60, 101, -80, -42, -1, -100, -93, -110, -113, -17, 91, -102, -28, -28, 24, -4, 21, -24, 62, -66, -96, -8, 127, -87, -1, 94, -19, 112, 5, 13, -19, 40, 73, -12, 123, -7, 89, -39, 86, -123, 12, -23, 41, -123, 31, 13, -127, 21, -10, 53, -79, 5, -18, 46, 78, 21, -48, 75, 36, 84, -65, 111, 79, -83, -16, 52, -79, 4, 3, 17, -100, -40, -29, -71, 47, -52, 91)
        ),
        newSecureAlgo=SecurePasswordKdfAlgoPBKDF2HMACSHA512iter100000Object(
            salt=byteArrayOf(-108, -84, -97, -1, -25, 90, -94, 92)
        ),
        secureRandom=byteArrayOf(106, 125, 14, -111, 13, -86, -8, -21, 15, 60, -119, -102, 52, -111, -44, -44, -112, 66, 81, 94, 79, 64, -61, -9, 78, 48, -48, -96, -35, -100, -54, -12, 11, 126, -103, -83, 9, -6, -125, -38, 41, -30, 32, -75, 57, 19, 48, 3, 67, -28, 15, 73, 80, -37, 53, 23, 41, 105, -49, -21, -30, 92, -88, 89, 60, 33, 63, 30, 60, 33, 121, -97, -36, 11, 46, 62, 100, -39, 97, -79, -113, -96, -7, 88, 91, 82, -48, 55, -27, -7, 27, -126, 124, -35, 43, 36, -102, -79, -96, 81, 13, 67, -24, -81, 47, -112, 94, 59, -71, -22, -67, 104, -40, 20, -56, -44, 34, 86, -101, 29, 104, -44, 110, -28, -98, -95, 17, -96, 20, 121, -39, -64, -102, 116, -42, 1, -55, -48, -89, -47, 19, 64, -55, 102, 50, 89, -124, 61, 63, 96, 28, -54, -84, -68, -46, 43, -127, 23, 85, 92, 34, 113, -22, 0, 21, 91, -117, -35, 106, 119, -13, 1, -45, -2, -65, 90, 100, 84, -82, -70, -81, 121, -128, 11, -65, 2, 17, -128, 96, -47, 55, 25, -14, -16, -58, -116, -25, 15, 7, 90, -16, -14, -48, 116, -93, 110, -94, -45, 71, -16, 13, 38, 69, 91, 127, -81, 17, 84, 8, -13, 47, -69, -120, -105, -90, 126, 7, -7, -42, 27, 97, -62, 86, -123, -22, -113, -37, -70, 104, 11, 31, 50, 64, 98, 12, -31, -10, -61, -49, 32, -53, 119, -1, 41, -9, 126)
    )
    val expectedA = byteArrayOf(46, 13, 16, 49, -59, 70, -66, 6, -44, 104, -65, 74, -6, 11, -62, 20, -41, -55, -78, -50, -3, 78, 8, -128, -114, 21, 46, 32, -107, 112, 27, 64, -21, 97, 18, 92, -61, -103, 2, -81, 70, -63, 74, 46, -80, 67, 2, 67, -20, -118, -26, -103, 104, 92, -61, -86, 104, -42, -116, 25, 23, -98, -125, -115, -22, 25, -61, 41, 84, 88, -86, -27, 18, -83, -49, 106, -100, -9, 3, -14, 84, -89, -10, 92, -70, 115, -68, 24, -26, -56, -94, 22, -5, -75, -7, -48, -125, -68, 92, -122, 126, 39, -70, -95, 127, 42, -97, -115, -51, -26, -50, -16, 72, -34, 76, 123, 75, 122, 111, 67, 51, -26, -16, 76, -94, -79, 40, -12, -14, -25, 74, 101, -32, -95, 100, 82, 37, 42, 58, 21, 105, 17, 59, -66, 96, -116, 111, 94, -99, -102, -23, 99, -83, -39, 66, 75, -73, -85, -89, 86, -36, 19, 32, -5, -41, 124, 83, 38, 20, -45, 12, -1, 90, -100, -90, -96, 101, -100, 15, -85, 14, -89, 1, 23, -112, -91, -1, 101, 80, -5, -19, -118, -33, 10, -128, 29, -44, 44, -128, 11, 110, 116, 36, 108, -119, 122, -61, -4, -117, 120, 32, -72, 36, 10, -97, 45, -2, -96, 101, 115, 127, 38, 28, 103, -15, 26, 48, 99, 124, -15, 117, -40, 27, 2, 99, -106, -112, -45, -50, 115, -29, -19, -124, 43, 33, -97, -97, -75, 104, 107, 19, -46, -4, 90, 48, 62)
    val expectedM1 = byteArrayOf(94, 13, -20, -87, 126, 86, -62, -91, -4, 0, -13, 110, -9, -110, -118, 41, 8, 59, -35, -118, 61, -67, -84, -54, 79, 61, 16, -59, -48, 3, -59, -86)

    @Test
    fun testSrpKeygen() {
        val srp = PasswordAuthenticator(::doPBKDF2SHA512Iter100000).hashPasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow("password1".toCharArray(), serverParams, serverParams.currentAlgo as PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPowObject, Random) { _, _ -> BigInteger(random) }
        assertEquals(InputCheckPasswordSRPObject(serverParams.srpId!!, expectedA, expectedM1), srp)
        // TODO this test fails due to issue #8
    }
}