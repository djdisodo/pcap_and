package com.gitlab.djdisodo.ksp.packetcapturedemo;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BufferPool {
	private static ConcurrentLinkedQueue<byte[]> pool = new ConcurrentLinkedQueue<>();

	public static byte[] get() {
		if (pool.isEmpty()) {
			pool.offer(new byte[1500]);
		}
		return pool.poll();
	}

	public static void release(byte[] buffer) {
		Arrays.fill(buffer, (byte) 0x00);
		pool.offer(buffer);
	}
}
