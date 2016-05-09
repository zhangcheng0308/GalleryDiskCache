package com.iqoo.secure.safeguard.extractthumbnail;

public class BytesBuffer {
	public byte[] data;
	public int offset;
	public int length;

    public BytesBuffer(int capacity) {
        this.data = new byte[capacity];
    }
}
