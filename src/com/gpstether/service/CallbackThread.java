/*
 *    GPSTether
 *    Copyright (C) 2009  Christoph Derigo <www.c99austria.com>
 *
 *
 *    GPSTether is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    GPSTether is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.gpstether.service;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;

import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

public class CallbackThread extends Thread implements LocationListener, GpsStatus.Listener {

	private static final String MODE_3D = "3";
	private static String satellites[] = new String[20];
	
	public boolean gps_online = false;

	private final RemoteCallbackList<ITetherServiceCallback> mCallbacks;

	private ServerSocket mServerSocket = null;
	
	private Location 		mLocation = null;
	private LocationManager mLocManager = null;
	
	private boolean mDone = false;
	private boolean mTag = false;	
		
	private int  mServerPort    = -1;
	private int  mTimeStampLow  =  0;
	private int  mNumSatellites =  0;
	
	private long mDiffAgeData  =  0;
	private long mTimeStampMS  = -1;
	private long mTimeStampUp  =  0;

	private float mRateOfClimb = 0;

	private double mLastAltitude = 0.0;
	
	private final List<EchoThread> mThreadList = new ArrayList<EchoThread>();
	// private UISync mUIInterface = null;
	private final Object sat_lock = new Object();

	// public CallbackThread(final UISync uii, final int port) {
	public CallbackThread(final int port, final LocationManager locMan,
			final RemoteCallbackList<ITetherServiceCallback> cb) {
		super();
		mCallbacks = cb;
		mLocManager = locMan;
		mServerPort = port;
	}

	/** Returns GGA NMEA sentence generated from raw data. */
	// $GPGGA,170834.00,4124.896300,N,08151.683800,W,1,05,1.5,280.2,M,-34.0,M,,,*75
	// $GPGGA,104438.833,4301.1439,N,08803.0338,W,1,05,1.8,185.8,M,-34.2,M,0.0,0000*40
	public String genGPGGA() {
		if (mLocation == null)
			return new String();

		// Start the NMEA position string
		String mPosNMEA = "$GPGGA,";

		final SimpleDateFormat formatter = new SimpleDateFormat("HHmmss.SS");
		final Calendar now = GregorianCalendar.getInstance();
		final long offset = now.getTime().getTimezoneOffset()*60000L;
		now.setTimeInMillis(mLocation.getTime() + offset);
		final String tempString = formatter.format(now.getTime());
		mPosNMEA += tempString.substring(0, 9) + ",";
		
		mPosNMEA += genGPGGA_GPGLL_long_lat_descr();
		mPosNMEA += "1," + mNumSatellites + ",";

		// HDOP
		mPosNMEA += "?,";
		mPosNMEA += mLocation.getAltitude() + ",M,,M,,,";
		mPosNMEA += "*" + NMEAChecksum(mPosNMEA) + Constants.COMMAND_END;

		return mPosNMEA;
	}

	/** Returns GLL NMEA sentence generated from raw data. */
	public String genGPGLL() {
		if (mLocation == null)
			return new String();

		// Start the NMEA position string
		String mPosNMEA = "$GPGLL,";
		mPosNMEA += genGPGGA_GPGLL_long_lat_descr();
		
		final SimpleDateFormat formatter = new SimpleDateFormat("HHmmss.SS");
		final Calendar now = GregorianCalendar.getInstance();
		final long offset = now.getTime().getTimezoneOffset() * 60000l;
		now.setTimeInMillis(mLocation.getTime() + offset);
		final String tempString = formatter.format(now.getTime());
		mPosNMEA += tempString.substring(0, 9) + ",A";

		mPosNMEA += "*" + NMEAChecksum(mPosNMEA) + Constants.COMMAND_END;
		return mPosNMEA;
	}

	/** Returns GSA NMEA sentence generated from raw data. */
	public String genGPGSA() {

		// Start the NMEA position string
		String mPosNMEA = "$GPGSA,";

		mPosNMEA += "A,";
		if (mNumSatellites > 0 && mNumSatellites <= 2) {
			mPosNMEA += "2";
		} else if (mNumSatellites > 3) {
			mPosNMEA += "3";
		} else {
			mPosNMEA += "0";
		}

		for (int i = 0; i < 12; i++)
			if (CallbackThread.satellites[i] != null) {
				mPosNMEA += "," + CallbackThread.satellites[i];
			} else {
				mPosNMEA += ",";
			}

		final float pdop = mLocation.getExtras().getFloat("pdop");
		final float hdop = mLocation.getExtras().getFloat("hdop");
		final float vdop = mLocation.getExtras().getFloat("vdop");

		mPosNMEA += "," + pdop;
		mPosNMEA += "," + hdop;
		mPosNMEA += "," + vdop;

		mPosNMEA += "*" + NMEAChecksum(mPosNMEA) + Constants.COMMAND_END;

		return mPosNMEA;
	}

	/** Returns RMC NMEA sentence generated from raw data. */
	// $GPRMC,081836.00,A,3751.65,S,14507.36,E,000.0,360.0,130998,011.3,E*62
	// $GPRMC,104748.821,A,4301.1492,N,08803.0374,W,0.085048,102.36,010605,,*1A
	// $GPRMC,104749.821,A,4301.1492,N,08803.0377,W,0.054215,74.60,010605,,*2D
	public String genGPRMC() {
		if (mLocation == null)
			return new String();

		// Start the NMEA position string
		String mPosNMEA = "$GPRMC,";

		final double lat = mLocation.getLatitude();
		final double lon = mLocation.getLongitude();
		final float kmh = mLocation.getSpeed() * Constants.MPS_TO_KMPH;
		final long time = mLocation.getTime();

		SimpleDateFormat formatter = new SimpleDateFormat("HHmmss.SS");
		final Calendar now = GregorianCalendar.getInstance();
		final long offset = now.getTime().getTimezoneOffset() * 60000l;
		now.setTimeInMillis(time + offset);
		String tempString = formatter.format(now.getTime());
		mPosNMEA += tempString.substring(0, 9) + ",";

		mPosNMEA += "A,";

		mPosNMEA += String.format("%02d", (int) Math.abs(lat));
		double temp = Math.abs(lat) - (int) Math.abs(lat);
		temp *= 60;
		mPosNMEA += String.format("%02.4f,", temp);
		if (lat > 0) {
			mPosNMEA += "N,";
		} else {
			mPosNMEA += "S,";
		}

		mPosNMEA += String.format("%03d", (int) Math.abs(lon));
		temp = Math.abs(lon) - (int) Math.abs(lon);
		temp *= 60;
		mPosNMEA += String.format("%02.4f,", temp);
		if (lon > 0) {
			mPosNMEA += "E,";
		} else {
			mPosNMEA += "W,";
		}

		mPosNMEA += kmh + ",";

		// Track made good
		mPosNMEA += "?,";

		formatter = new SimpleDateFormat("ddMMyy");
		tempString = formatter.format(now.getTime());
		mPosNMEA += tempString + ",";

		// Magnetic variation, direction, FAA mode
		mPosNMEA += "?,?,";
		mPosNMEA += "*" + NMEAChecksum(mPosNMEA) + Constants.COMMAND_END;

		return mPosNMEA;
	}

	private void getAllSatellites() {
		final GpsStatus gps_status = mLocManager.getGpsStatus(null);
		synchronized (sat_lock) {
			final Iterator<GpsSatellite> sats = gps_status.getSatellites()
					.iterator();
			mNumSatellites = 0;
			while (sats.hasNext()) {
				final GpsSatellite temp = sats.next();
				CallbackThread.satellites[mNumSatellites] = Integer
						.toString(temp.getPrn());
				mNumSatellites++;
			}
			sat_lock.notifyAll();
		}
	}

	/**
	 * Returns the current altitude in the form "A=f";
	 */
	public String getAltitude() {
		return ",A=" + mLocation.getAltitude();
	}

	public String getBearing() {
		return ",T=" + mLocation.getBearing();
	}

	public String getLastKnownLocation() {
		if (mLocManager == null)
			return ",V=?";
		// 1 Meter per Second = 1.9438444924406 Knots
		final Location location = mLocManager.getLastKnownLocation("gps");
		return ",V=" + location.getSpeed() * Constants.M_TO_KNOTS; // in
	}

	/**
	 * RMC - Recommended Minimum Navigation Information
	 * 
	 * 
	 * 12 1 2 3 4 5 6 7 8 9 10 11| | | | | | | | | | | | |
	 * $--RMC,hhmmss.ss,A,llll.ll,a,yyyyy.yy,a,x.x,x.x,xxxx,x.x,a*hh<CR><LF>
	 */

	/* Returns Nav Information in RMC and GGA */
	public String getNavInfo() {
		if (mLocation == null)
			return ",O=?";

		final double lat = mLocation.getLatitude();
		final double lon = mLocation.getLongitude();
		final float  kmh = mLocation.getSpeed() * Constants.MPS_TO_KMPH;

		String ret = ",O=";
		ret += mTag ? "GGA " : "RMC ";
		mTag = !mTag;

		ret += mTimeStampUp + "." + mTimeStampLow + " 0.005 " + lat + " " + lon
				+ " " + mLocation.getAltitude() + " ? ? "
				+ mLocation.getBearing() + " " + kmh + " 0.000 ? ? ? "
				+ CallbackThread.MODE_3D;
		
		return ret;
	}

	/**
	 * Returns the current position in the form "P=%f %f"; numbers are in
	 * degrees, latitude first.
	 */
	public String getPosition() {
		if (mLocManager == null)
			return ",P=?";
		final Location location = mLocManager.getLastKnownLocation("gps");

		if (location == null)
			return ",P=?";
		return ",P=" + location.getLatitude() + " " + location.getLongitude();
	}

	public String getRateOfClimb() {
		return "U=" + mRateOfClimb;
	}

	/** Returns RAW NMEA sentences */
	public String getRawInfo() {
		String temp;
		temp = genGPGSA() + genGPGGA() + genGPGLL() + genGPRMC();

		return temp;
	}

	/** Returns GGA NMEA sentence generated from raw data. */
	public String getUTCTime() {
		if (mLocation == null)
			return new String();

		String ret = ",D=";

		final long time = mLocation.getTime(); // This is the correct way to do it.
		final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'");
		final Calendar now = GregorianCalendar.getInstance();
		final long offset = now.getTime().getTimezoneOffset() * 60000l;
		now.setTimeInMillis(time + offset);
		ret += formatter.format(now.getTime());
		ret += Constants.COMMAND_END;

		return ret;
	}

	String NMEAChecksum(final String sentence) {
		// Calculate checksum...
		char cksum = 0;

		for (int count = 0; count < sentence.length(); count++) {
			if (sentence.charAt(count) != '$') {
				cksum = (char) (cksum ^ sentence.charAt(count));
			}
		}
		return Integer.toString((cksum & 0xff) + 0x100, 16 /* radix */)
				.substring(1).toUpperCase();
	}

	@Override
	public void onGpsStatusChanged(final int event) {
		if (mLocManager == null)
			return;
		switch (event) {
		case GpsStatus.GPS_EVENT_STARTED:
			gps_online = true;
			break;
		case GpsStatus.GPS_EVENT_STOPPED:
			gps_online = false;
			break;
		case GpsStatus.GPS_EVENT_FIRST_FIX:
			break;
		case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
			getAllSatellites();
			break;
		}
	}

	// private float lastSpeed = 0;

	@Override
	public void onLocationChanged(final Location location) {
		if (location != null) {
			Log.v("gpsd", "onLocationChanged callback was called!");

			if (mTimeStampMS != -1) {
				// in meters per second
				mDiffAgeData = location.getTime() - mTimeStampMS;
				mRateOfClimb = (float) (mLastAltitude - location.getAltitude())
						* 1000.0f / mDiffAgeData;

				if (!location.hasBearing()) {
					final float bearing = mLocation.bearingTo(location);
					mLocation.setBearing(bearing);
				}
				/*
				 * if(location.hasSpeed()){ lastSpeed = location.getSpeed(); }
				 * else { location.setSpeed(lastSpeed); }
				 */
			}
			mLocation = location;

			mTimeStampMS = location.getTime();
			mTimeStampUp = mTimeStampMS / 1000;
			mTimeStampLow = (int) (mTimeStampMS % 1000) / 10;
			mLastAltitude = location.getAltitude();
			mLastAltitude = location.getAltitude();

			sendLocChangeToClient();
		} else {
			Log.d("gpsd", "onLocationChanged callback was called with NULL Location!!");
		}
	}

	public void onProviderDisabled(final String provider) {
	}

	public void onProviderEnabled(final String provider) {
	}

	@Override
	public void onStatusChanged(final String provider, final int status,
			final Bundle extras) {
	}

	/**
	 * @return "Q=%d %f %f %f %f %f": a count of satellites used in the last
	 *         fix, and five dimensionless dilution-of-precision (DOP) numbers —
	 *         spherical, horizontal, vertical, time, and total geometric. These
	 *         are computed from the satellite geometry; they are factors by
	 *         which to multiply the estimated UERE (user error in meters at
	 *         specified confidence level due to ionospheric delay, multipath
	 *         reception, etc.) to get actual circular error ranges in meters
	 *         (or seconds) at the same confidence level. See also the 'e'
	 *         command. Note: Some GPSes may fail to report these, or report
	 *         only one of them (often HDOP); a value of 0.0 should be taken as
	 *         an indication that the data is not available.
	 */
	public String printOutSatellites() {
		return ",Q=" + mNumSatellites + " ? ? ? ? ? ";
	}

	public void requestExitAndWait() {
		mDone = true;
		// Unregister all callbacks.
		mCallbacks.kill();

		for (int i = 0; i < mThreadList.size(); i++) {
			mThreadList.get(i).requestExitAndWait();
			Log.d(toString(), "Thread Nr " + i + 1 + " closed now!");
		}
		// Tell the thread to quit
		try {
			if (mServerSocket != null) {
				mServerSocket.close();
			}
			this.join();
		} catch (final InterruptedException ex) {
			// Ignore
		} catch (final IOException e) {
			Log.i(toString(), "Error on server socket close!");
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		// first open the server socket!
		Log.d(toString(), "Worker Thread Started!");
		try {
			mServerSocket = new ServerSocket(mServerPort);
			mServerSocket.setSoTimeout(Constants.SOCKET_TIMEOUT); // send
															  	  // timeout
																  // ?!
		} catch (final Exception e) {
			Log.e("gpsd", "Error on Server Socket Connect!:");
			e.printStackTrace();
			return;
		}
		Log.d(toString(), "Server Socket UP and Rolling!");
		while (!mDone) {
			Socket clientSocket = null;
			try {
				clientSocket = mServerSocket.accept();
				clientSocket.setKeepAlive(true);
			} catch (final SocketTimeoutException e) {
				// Log.v("gpsd","Polling Clientport : "+mServerPort);
				continue; // new client connect poll!!!
			} catch (final IOException e) {
				e.printStackTrace();
				break;
			}
			Log.d(toString(), "Client got connected on Port: " + mServerPort);
			final EchoThread thread = new EchoThread(clientSocket, this);
			mThreadList.add(thread);
			thread.start();
		}
		Log.d(getClass().toString(), "Callback Thread Done!");
	}

	public void sendLocChangeToClient() {
		Log.v(getClass().toString(),
			"================Send Loc Change to Client called!============");
		if (mCallbacks == null) {
			Log.e(getClass().toString(), "Error: mCallbacks was null!");
			return;
		}

		synchronized (mCallbacks) {
			final int N = mCallbacks.beginBroadcast();
			if (N > 0) { // its imortant to call finishBroadcast even if N == 0!
				if (mLocation == null) {
					Log.v(getClass().getClass().toString(),
						"Error, ==> location == null, sendLocChangeToClient called to soon ?");
					return;
				}
				final String send_str = mLocation.toString();
				for (int i = 0; i < N; i++) {
					try {
						Log.v(toString(), "sending : " + send_str);
						mCallbacks.getBroadcastItem(i).gpsChanged(send_str);
					} catch (final RemoteException e) {
						// The RemoteCallbackList will take care of removing
						// the dead object for us.
						Log.d(getClass().toString(), "RemoteException occured!");
					}
				}
			}
			mCallbacks.finishBroadcast();
		}
	}

	public String sendXMode(final boolean send_i) {
		if (send_i)	return ",X=" + mTimeStampUp + "." + mTimeStampLow + ",I=Generic NMEA";
					return ",X=" + mTimeStampUp + "." + mTimeStampLow;
	}
	
	private String genGPGGA_GPGLL_long_lat_descr()
	{	
		final double lat = mLocation.getLatitude();
		final double lon = mLocation.getLongitude();
		
		String ret =  String.format("%02d", (int) Math.abs(lat));
			   ret += String.format("%02.4f,", 
					   	(Math.abs(lat) - (int) Math.abs(lat))*60);
			   ret += (lat > 0)?"N,":"S,";
		
			   ret += String.format("%03d", (int) Math.abs(lon));
			   ret += String.format("%02.4f,", 
					   (Math.abs(lon) - (int) Math.abs(lon))*60);
			   ret += (lon > 0) ? "E,": "W,";
		return ret;
	}
			
}
