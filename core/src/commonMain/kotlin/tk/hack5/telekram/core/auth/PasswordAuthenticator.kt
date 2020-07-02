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

package tk.hack5.telekram.core.auth


import com.github.aakira.napier.Napier
import com.soywiz.krypto.SecureRandom
import com.soywiz.krypto.sha256
import org.gciatto.kt.math.BigInteger
import tk.hack5.telekram.core.crypto.toByteArray
import tk.hack5.telekram.core.crypto.toCharArray
import tk.hack5.telekram.core.tl.*
import tk.hack5.telekram.core.tl.toByteArray
import tk.hack5.telekram.core.utils.pad

open class PasswordAuthenticator(protected val pbkdf2sha512: (CharArray, ByteArray) -> ByteArray) {
    fun hashPasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow(
        password: CharArray,
        serverParams: Account_PasswordObject,
        algo: PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPowObject,
        secureRandom: SecureRandom
    ): InputCheckPasswordSRPType {
        Napier.d(serverParams.toString())
        val passwordBytes = password.toByteArray()

        // TODO https://github.com/korlibs/krypto/issues/12
        val g = algo.g
        val gByteArray = g.toByteArray(256)
        val gBigInt = BigInteger.of(g)
        val p = algo.p.pad(256)
        val pBigInt = BigInteger(byteArrayOf(0) + p)

        val salt1 = algo.salt1.pad(256)
        val salt2 = algo.salt2.pad(256)

        val a = getSecureNonce(256, secureRandom)
        val aByteArray = a.toByteArray(256)
        val gA = gBigInt.modPow(a, pBigInt)!!
        val gAByteArray = gA.toByteArray(256)
        val gB = serverParams.srpB!!.pad(256)
        val gBBigInt = BigInteger(gB)

        val k = (p + gByteArray).sha256()
        val kBigInt = BigInteger(k)

        val u = (gA.toByteArray(256) + gB).sha256()
        val uBigInt = BigInteger(u)

        val x = secondaryPasswordHash(passwordBytes, salt1, salt2)
        val xBigInt = BigInteger(x)
        val v = gBigInt.modPow(xBigInt, pBigInt)!!

        val kV = (kBigInt * v).modPow(BigInteger.ONE, pBigInt)!!

        val t = (gBBigInt - kV).modPow(BigInteger.ONE, pBigInt)!!.let {
            if (it < BigInteger.ZERO) it + pBigInt else it
        }
        val sA = t.modPow(a + uBigInt * t, pBigInt)!!
        val kA = sA.toByteArray(256).sha256()
        val m1 = ((p.sha256() xor gByteArray) + salt1.sha256() + salt2.sha256() + gAByteArray + gB + kA).sha256()
        return InputCheckPasswordSRPObject(serverParams.srpId!!, aByteArray, m1)
    }

    protected fun saltingHash(data: ByteArray, salt: ByteArray) = (salt + data + salt).sha256()

    protected fun primaryPasswordHash(password: ByteArray, salt1: ByteArray, salt2: ByteArray): ByteArray =
        saltingHash(saltingHash(password, salt1), salt2)

    protected fun secondaryPasswordHash(password: ByteArray, salt1: ByteArray, salt2: ByteArray): ByteArray =
        saltingHash(pbkdf2sha512(primaryPasswordHash(password, salt1, salt2).toCharArray(), salt1), salt2)
}

internal infix fun ByteArray.xor(other: ByteArray): ByteArray =
    mapIndexed { i, it -> it.toInt().xor(other[i].toInt()).toByte() }.toByteArray()