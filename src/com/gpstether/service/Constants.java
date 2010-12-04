package com.gpstether.service;

public final class Constants {
	public static final int 		CLIENT_WAIT_TIMEOUT 		= 5000;
	public static final int 		SOCKET_TIMEOUT 				= 1000; // 1 sec	
	
	public static final long 		GPS_UPDATE_MS_TH 			= 200L; // 5 times a second!
	public static final float 		GPS_UPDATE_METERS_TH     	= 1.0f; // every meter
	
	public static final long 		NETW_UPDATE_MS_TH 			= 200L; // 5 times a second!
	public static final float 		NETW_UPDATE_METERS_TH     	= 500.0f; // every 500 meters
	
	public static final String 		COMMAND_END 				= "\r\n";
	public static final String 		REPLY_START 				= "GPSD";

	// 1 Meter per Second = 1.9438444924406 Knots
	public static final double 		M_TO_KNOTS 					= 1.9438444924406;
	
	// 1 Meter per Second = 3.6 Kilometers per Hour
	public static final float 		MPS_TO_KMPH					= 3.6f;
	public static final float 		KMPH_TO_MPS					= 2.7777777777778f;
	
	public static final String 		GPSD_DEVICE_NAME			= "F,Android GPS Device";
	
	public static final int 		TRUE						=  1;
	public static final int 		FALSE						=  0;
	public static final int 		UNKNOWN						= -1;
}
