package com.gpstether.service;

public final class Constants {
	public static final float gpsUpdateThresholdInMeters = 1.0f; // 1 meter
	public static final long  gpsUpdateTimeMsec = 200L; // every 200 ms!!!
	public static final int   SOCKET_TIMEOUT = 1000; // 1 sec
	public static final String COMMAND_END = "\r\n";
	public static final String REPLY_START = "GPSD";
	public static final int    CLIENT_WAIT_TIMEOUT = 5000;

}
