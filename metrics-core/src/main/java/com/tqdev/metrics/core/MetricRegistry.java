/* Copyright (C) 2017 Maurits van der Schee
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.tqdev.metrics.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

// TODO: Auto-generated Javadoc
/**
 * The Class MetricRegistry.
 */
public class MetricRegistry {

	/** The values. */
	private volatile ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> values;

	/**
	 * Instantiates a new metric registry.
	 */
	public MetricRegistry() {
		values = new ConcurrentHashMap<>();
	}

	/**
	 * Increment.
	 *
	 * @param type
	 *            the type
	 * @param key
	 *            the key
	 */
	public void increment(String type, String key) {
		((LongAdder) values.computeIfAbsent(type, t -> new ConcurrentHashMap<>()).computeIfAbsent(key,
				k -> new LongAdder())).increment();
	}

	/**
	 * Decrement.
	 *
	 * @param type
	 *            the type
	 * @param key
	 *            the key
	 */
	public void decrement(String type, String key) {
		((LongAdder) values.computeIfAbsent(type, t -> new ConcurrentHashMap<>()).computeIfAbsent(key,
				k -> new LongAdder())).decrement();
	}

	/**
	 * Adds the.
	 *
	 * @param type
	 *            the type
	 * @param key
	 *            the key
	 * @param value
	 *            the value
	 */
	public void add(String type, String key, long value) {
		((LongAdder) values.computeIfAbsent(type, t -> new ConcurrentHashMap<>()).computeIfAbsent(key,
				k -> new LongAdder())).add(value);
	}

	/**
	 * Sets the.
	 *
	 * @param type
	 *            the type
	 * @param key
	 *            the key
	 * @param value
	 *            the value
	 */
	public void set(String type, String key, long value) {
		LongAdder adder = new LongAdder();
		adder.add(value);
		values.computeIfAbsent(type, t -> new ConcurrentHashMap<>()).put(key, adder);
	}

	/**
	 * Sets the.
	 *
	 * @param type
	 *            the type
	 * @param key
	 *            the key
	 * @param value
	 *            the value
	 */
	public void set(String type, String key, Gauge value) {
		values.computeIfAbsent(type, t -> new ConcurrentHashMap<>()).put(key, value);
	}

	/**
	 * Gets the types.
	 *
	 * @return the types
	 */
	public Iterable<String> getTypes() {
		return values.keySet();
	}

	/**
	 * Gets the keys.
	 *
	 * @param type
	 *            the type
	 * @return the keys
	 * @throws NullPointerException
	 *             the null pointer exception
	 */
	public Iterable<String> getKeys(String type) throws NullPointerException {
		return values.get(type).keySet();
	}

	/**
	 * Checks for.
	 *
	 * @param type
	 *            the type
	 * @param key
	 *            the key
	 * @return true, if successful
	 * @throws NullPointerException
	 *             the null pointer exception
	 */
	public boolean has(String type, String key) throws NullPointerException {
		return values.get(type).containsKey(key);
	}

	/**
	 * Gets the.
	 *
	 * @param type
	 *            the type
	 * @param key
	 *            the key
	 * @return the long
	 * @throws NullPointerException
	 *             the null pointer exception
	 */
	public long get(String type, String key) throws NullPointerException {
		Object o = values.get(type).get(key);
		if (o instanceof LongAdder) {
			return ((LongAdder) o).sum();
		} else if (o instanceof Gauge) {
			return ((Gauge) o).measure();
		} else {
			return -1;
		}
	}

	// singleton pattern

	/** The single instance of Storage. */
	private static final MetricRegistry instance = new MetricRegistry();

	/**
	 * Gets the single instance of the Storage.
	 *
	 * @return single instance of Storage
	 */
	public static MetricRegistry getInstance() {
		return instance;
	}

}
