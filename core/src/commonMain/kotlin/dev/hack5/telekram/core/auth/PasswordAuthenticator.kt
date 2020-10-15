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


import com.github.aakira.napier.Napier
import com.soywiz.krypto.sha256
import dev.hack5.telekram.core.crypto.toByteArray
import dev.hack5.telekram.core.tl.Account_PasswordObject
import dev.hack5.telekram.core.tl.InputCheckPasswordSRPObject
import dev.hack5.telekram.core.tl.InputCheckPasswordSRPType
import dev.hack5.telekram.core.tl.PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPowObject
import dev.hack5.telekram.core.utils.pad
import org.gciatto.kt.math.BigInteger
import kotlin.random.Random


open class PasswordAuthenticator(protected val pbkdf2sha512: (ByteArray, ByteArray) -> ByteArray) {
    fun hashPasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow(
        password: CharArray,
        serverParams: Account_PasswordObject,
        algo: PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPowObject,
        secureRandom: Random,
        secureNonce: (Int, Random) -> BigInteger = ::getSecureNonce
    ): InputCheckPasswordSRPType {
        Napier.d(serverParams.toString())
        val passwordBytes = password.toByteArray()
        println(passwordBytes.contentToString())

        // TODO https://github.com/korlibs/krypto/issues/12
        val g = algo.g
        val gBigInt = BigInteger.of(g)
        val gByteArray = gBigInt.toByteArray().pad(256)
        val p =
            algo.p.pad(256) // TODO verify this is a safe 2048 bit prime and g generates a cyclic subgroup of prime order (p-1)/2
        val pBigInt = BigInteger(byteArrayOf(0) + p)

        val salt1 = algo.salt1
        val salt2 = algo.salt2

        val a = secureNonce(256, secureRandom)
        val gA = gBigInt.modPow(a, pBigInt)
        val gAByteArray = gA.toByteArray().pad(256)
        val gB = serverParams.srpB!!.pad(256)
        val gBBigInt = BigInteger(byteArrayOf(0) + gB)

        val k = (p + gByteArray).sha256().bytes
        val kBigInt = BigInteger(byteArrayOf(0) + k)

        val u = (gAByteArray + gB).sha256().bytes
        val uBigInt = BigInteger(byteArrayOf(0) + u)

        val x = secondaryPasswordHash(passwordBytes, salt1, salt2)
        val xBigInt = BigInteger(byteArrayOf(0) + x)
        val v = gBigInt.modPow(xBigInt, pBigInt)

        val kV = (kBigInt * v).modPow(BigInteger.ONE, pBigInt)

        val t = (gBBigInt - kV).modPow(BigInteger.ONE, pBigInt).let {
            if (it < BigInteger.ZERO) it + pBigInt else it
        }
        val sA = t.modPow(a + uBigInt * xBigInt, pBigInt)
        val sAByteArray = sA.toByteArray().pad(256)
        val kA = sAByteArray.sha256().bytes
        println(x.contentToString())
        println(xBigInt)
        println(v)
        println(kBigInt)
        println(gBBigInt)
        println(kV)
        println(pBigInt)
        println(t)
        println(sA)
        println(sAByteArray.contentToString())
        println(kA.contentToString())
        val m1 =
            ((p.sha256().bytes xor gByteArray.sha256().bytes) + salt1.sha256().bytes + salt2.sha256().bytes + gAByteArray + gB + kA).sha256().bytes
        return InputCheckPasswordSRPObject(serverParams.srpId!!, gAByteArray, m1)
    }

    protected fun saltingHash(data: ByteArray, salt: ByteArray) = (salt + data + salt).sha256().bytes

    protected fun primaryPasswordHash(password: ByteArray, salt1: ByteArray, salt2: ByteArray): ByteArray =
        saltingHash(saltingHash(password, salt1), salt2)

    protected fun secondaryPasswordHash(password: ByteArray, salt1: ByteArray, salt2: ByteArray): ByteArray {
        println("hash2 = ${primaryPasswordHash(password, salt1, salt2).contentToString()}")
        val hash3 = pbkdf2sha512(primaryPasswordHash(password, salt1, salt2), salt1)
        print("hash3 = ")
        println(hash3.contentToString())
        return saltingHash(hash3, salt2)
    }
}

internal infix fun ByteArray.xor(other: ByteArray): ByteArray {
    require(size == other.size) { "$size != ${other.size}" }
    return mapIndexed { i, it -> it.toInt().xor(other[i].toInt()).toByte() }.toByteArray()
}