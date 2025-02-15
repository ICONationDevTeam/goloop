/*
 * Copyright 2021 ICON Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package example.util;

import score.ObjectReader;
import score.ObjectWriter;

import java.math.BigInteger;

public class IntSet {
    private final String id;
    private final EnumerableMap<BigInteger, BigInteger> map;

    public IntSet(String id) {
        this.id = id;
        this.map = new EnumerableMap<>(id, BigInteger.class);
    }

    // for serialize
    public static void writeObject(ObjectWriter w, IntSet e) {
        w.write(e.id);
    }

    // for de-serialize
    public static IntSet readObject(ObjectReader r) {
        return new IntSet(
                r.readString()
        );
    }

    public int length() {
        return map.length();
    }

    public BigInteger at(int index) {
        return map.get(index);
    }

    public void add(BigInteger value) {
        map.set(value, value);
    }

    public void remove(BigInteger value) {
        var lastValue = at(length() - 1);
        map.popAndSwap(value, lastValue);
    }
}
