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

package dev.hack5.telekram.api.iter

import com.github.aakira.napier.Napier
import kotlinx.coroutines.flow.*

internal suspend inline fun <R, O> iter(initialValue: O, crossinline function: suspend (O) -> Pair<Collection<R>, O>) = flow {
    var lastOutput = function(initialValue)
    while (lastOutput.second != null) {
        if (lastOutput.first.isEmpty() && lastOutput.second != null)
            Napier.w("iter function returned empty collection", tag = tag)
        else
            lastOutput.first.forEach { emit(it) }
        if (lastOutput.second == null) break
        lastOutput = function(lastOutput.second)
    }
}


abstract class RandomAccessIter<I : Comparable<I>, R, O> {
    open suspend fun get(range: ClosedRange<I>): Flow<Pair<ClosedRange<I>, R>> =
        iter(getInitialParameters(range.start, range.endInclusive), ::get)
            .dropWhile { it.first.endInclusive < range.start }
            .takeWhile { it.first.start <= range.endInclusive }


    abstract suspend fun get(data: O): Pair<Collection<Pair<ClosedRange<I>, R>>, O>
    abstract suspend fun getInitialParameters(start: I, endInclusive: I?): O
}

abstract class RandomAccessBulkIter<I : Comparable<I>, R, O> : RandomAccessIter<I, Collection<R>, O>() {
    override suspend fun get(range: ClosedRange<I>): Flow<Pair<ClosedRange<I>, Collection<R>>> = super.get(range).map {
        when {
            it.first.start < range.start && it.first.endInclusive >= range.start -> {
                // edge case: chunk starts before start of target range, but ends after start of target range
                // remove unwanted elements
                it.first.start .. range.endInclusive to it.second.drop(it.second.size - (subtractIndices(range.start, it.first.endInclusive) - 1))
            }
            it.first.endInclusive > range.endInclusive && it.first.start <= range.endInclusive -> {
                // edge case: chunk ends after end of target range, but starts before end of target range
                // remove unwanted elements
                range.start .. it.first.endInclusive to it.second.take(subtractIndices(it.first.start, range.endInclusive) + 1)
            }
            else -> it
        }
    }

    abstract fun subtractIndices(left: I, right: I): Int
}

private const val tag = "IterTools"
