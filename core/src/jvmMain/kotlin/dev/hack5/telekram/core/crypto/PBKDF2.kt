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

package dev.hack5.telekram.core.crypto

import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter


internal actual fun doPBKDF2SHA512Iter100000(password: ByteArray, salt: ByteArray): ByteArray {
    val gen = PKCS5S2ParametersGenerator(SHA512Digest())
    gen.init(password, salt, 100000)
    val dk = (gen.generateDerivedParameters(512) as KeyParameter).key
    gen.password.let {
        it.indices.forEach { i ->
            it[i] = 0
        }
    }
    return dk
    /*val pbe = PBEKeySpec(password.map { it.toUByte().toInt().toChar() }.toCharArray(), salt, 100000, 512)
    val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
    val ret = skf.generateSecret(pbe).encoded
    pbe.clearPassword()
    return ret*/
}