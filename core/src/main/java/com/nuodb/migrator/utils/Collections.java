/**
 * Copyright (c) 2014, NuoDB, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of NuoDB, Inc. nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL NUODB, INC. BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.nuodb.migrator.utils;

import com.nuodb.migrator.jdbc.metadata.MetaDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * @author Sergey Bushik
 */
@SuppressWarnings("unchecked")
public class Collections {

    public static <T> PrioritySet<T> newPrioritySet() {
        return new SimplePrioritySet<T>();
    }

    public static <T> PrioritySet<T> newPrioritySet(PrioritySet<T> prioritySet) {
        return new SimplePrioritySet<T>(prioritySet);
    }

    public static <T> boolean contains(Collection<T> collection, T value) {
        return collection != null && collection.contains(value);
    }

    public static <T> boolean addIgnoreNull(Collection<T> collection, T value) {
        return value != null && collection.add(value);
    }

    public static boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isEmpty(Map map) {
        return map == null || map.isEmpty();
    }

    public static boolean isEmpty(Object[] values) {
        return values == null || values.length == 0;
    }

    public static <K, V> Map<K, V> putAll(Map<K, V> target, Map<K, V> source) {
        if (source != null) {
            target.putAll(source);
        }
        return target;
    }

    public static <T> Collection<T> removeAll(Collection<T> collection, Collection<T> remove) {
        if (collection != null && remove != null) {
            collection.removeAll(remove);
        }
        return collection;
    }
}
