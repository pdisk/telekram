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

package tk.hack5.telekram.core.tl

import tk.hack5.telekram.core.mtproto.MessageObject
import kotlin.test.Test

class ComplexDeserializationTest {
    private val message = intArrayOf(52237313, 1595449205, 734, 952, 1945237724, 9, 492431361, 1595448515, 229, 332, -212046591, 72000000, 1595448515, 812830625, 79358, 559903, 0, 2012939008, 1712508780, 956696070, 395456032, -1917779497, -1411522914, -3037259, -1020850377, 105786953, -993153776, 1930036736, 11785731, -23, -432228393, -513109916, -234592623, -1001756297, 718872203, -223911421, 3324137, -1719853031, 1611516722, 597519846, 1545391889, 122814554, -1704047054, 1158112724, 1552942641, -704554002, 1623536430, -455831383, -1560479988, -1258355372, -857420620, 1619336388, -419073865, 101893260, -1742956514, -177159993, -637825930, -1233390237, -1974231334, -1698434555, -107996775, -935444463, -416964179, -1754382110, -811986592, 1980023504, -73399813, -1323600018, -258729677, -434916382, 756122535, 880915347, 1527398344, -227198884, 984669731, 1452783658, 99627058, 111601639, -458889691, -956759321, -676250356, 1851452639, 189926696, 1604833138, -81761063, 816955043, -2140933344, -1629908868, -555795562, 252783519, -552457216, 101484, 0, 1067211777, 1595448515, 231, 28, -212046591, 700000000, 1595448515, 1041346555, 3, 335264, 30, 1636277249, 1595448515, 233, 28, -212046591, 1232000000, 1595448515, 1041346555, 3, 199373, 30, -2086802431, 1595448515, 235, 28, -212046591, 1760000000, 1595448515, 1041346555, 3, 465132, 30, -1540812799, 1595448515, 237, 28, -212046591, -2002967296, 1595448515, 1041346555, 3, 648068, 30, -970224639, 1595448515, 239, 28, -212046591, -1486967296, 1595448515, 1041346555, 3, 905264, 30, -804779007, 1595448515, 241, 272, 812830625, 67838, 559903, 0, 1886585600, 2049070938, 588032868, 1304625923, -1454809706, 229522044, 1977912896, -1478687211, -1634895553, -81011234, -251906869, 1450739996, -489900803, -1744435700, -279059462, 51675251, 1014435883, -1996463792, 1175381185, 130960564, -197975854, 2087976603, 989051641, 1247699895, -2101777839, 710828623, 2034791543, -2017293978, -967404551, 1855488114, 1084869001, -676250123, 1633283295, 2010254632, 519919862, 1826207691, -616940768, -107997095, -935444259, -112291938, -898211697, -323316915, 12765344, -611799182, 324737993, 1165102962, 28846585, -1751540387, -1789476658, -708774393, 552481243, -319845709, 1972963298, 26057282, -938836071, 902955623, 1105393831, -1592391300, 1277955321, -244, 201605039, 1609735, 371221254, 312, -368471039, 1595448515, 243, 28, -212046591, -962967296, 1595448515, 1041346555, 3, 289288, 30, 178114561, 1595448516, 245, 28, -212046591, -398967296, 1595448515, 1041346555, 3, 335264, 30, -1401688704, -808652887, -760536810, 686364451, -1606932036, -1149163254)
    @Test
    fun testComplexDeserialization() {
        MessageObject.fromTlRepr(message, true)
    }
}