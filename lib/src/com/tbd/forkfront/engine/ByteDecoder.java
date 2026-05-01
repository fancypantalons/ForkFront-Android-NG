package com.tbd.forkfront.engine;

public interface ByteDecoder
{
	char decode(int b);
	String decode(byte[] bytes);
}
