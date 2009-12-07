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
import java.util.ArrayList;
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

    private final RemoteCallbackList<ITetherServiceCallback> mCallbacks;

	// private static final String GSV = "GSV ";
	private static final String GGA = "GGA ";
	private static final String RMC = "RMC ";
	
	private static final double M_TO_KNOTS = 1.9438444924406;

	private static final String MODE_3D = "3";
	private static final float MS_TO_KMH = 1000f / 3600f;	

	public boolean gps_online = false;
	
	private long mDiffAgeData = 0;
	private boolean mDone = false;
	private double mLastAltitude = 0.0;

	private Location mLocation = null;
	private LocationManager mLocManager = null;

	private int mNumSatellites = 0;
	private float mRateOfClimb = 0;

	private ServerSocket mServerSocket = null;
	private int mServerPort = -1;
	private boolean mTag = false;
	private final List<EchoThread> mThreadList = new ArrayList<EchoThread>();

	private int mTimeStampLow = 0;
	private long mTimeStampMS = -1;

	private long mTimeStampUp = 0;

	//private UISync mUIInterface = null;
	private final Object sat_lock = new Object();
	
	//public CallbackThread(final UISync uii, final int port) {
	public CallbackThread(final int port, LocationManager locMan, RemoteCallbackList<ITetherServiceCallback> cb) {
		super();
		mCallbacks = cb; 
		mLocManager = locMan;
		this.mServerPort = port;
	}

	private void getAllSatellites() {
		final GpsStatus gps_status = mLocManager.getGpsStatus(null);
		synchronized (this.sat_lock) {
			final Iterator<GpsSatellite> sats = gps_status.getSatellites().iterator();
			this.mNumSatellites = 0;
			while (sats.hasNext()) {
				sats.next();
				this.mNumSatellites++;
			}
			this.sat_lock.notifyAll();
		}
	}

	public String getBearing() {
		return ",T=" + this.mLocation.getBearing();
	}

	public String getLastKnownLocation() {
		if (mLocManager == null) {
			return ",V=?";
		}
		final Location location = mLocManager.getLastKnownLocation("gps");
		return ",V=" + (location.getSpeed() * CallbackThread.M_TO_KNOTS); // in
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
		if (this.mLocation == null) return ",O=?";

		final double lat = this.mLocation.getLatitude();
		final double lon = this.mLocation.getLongitude();
		final float kmh  = this.mLocation.getSpeed() * CallbackThread.MS_TO_KMH;

		String ret = ",O=";
		ret += this.mTag ? CallbackThread.GGA : CallbackThread.RMC;
		this.mTag = !this.mTag;
				
		//Log.v(this.toString(),"getNavInfo: has Altitude: "+this.mLocation.hasAltitude() + " has accuracy "+this.mLocation.hasAccuracy() + "has speed" +this.mLocation.hasSpeed()+" has bearing "+this.mLocation.hasBearing());		
		
		ret += this.mTimeStampUp + "." + this.mTimeStampLow + " 0.005 " + lat
				+ " " + lon + " " + this.mLocation.getAltitude() + " ? ? "
				+ this.mLocation.getBearing() + " " + kmh + " 0.000 ? ? ? "
				+ CallbackThread.MODE_3D;
		return ret;
	}
	
	/**
	 * Returns the current position in the form "P=%f %f"; numbers are in
	 * degrees, latitude first.
	 */
	public String getPosition() {
		if (mLocManager == null) {
			return ",P=?";
		}
		final Location location = mLocManager.getLastKnownLocation("gps");

		if (location == null) {
			return ",P=?";
		}
		return (",P=" + location.getLatitude() + " " + location.getLongitude());
	}

	public String getRateOfClimb() {
		return "U=" + this.mRateOfClimb;
	}

	@Override
	public void onGpsStatusChanged(final int event) {
		if (mLocManager == null) {
			return;
		}
		switch (event) {
		case GpsStatus.GPS_EVENT_STARTED:
			this.gps_online = true;
			break;
		case GpsStatus.GPS_EVENT_STOPPED:
			this.gps_online = false;
			break;
		case GpsStatus.GPS_EVENT_FIRST_FIX:
			break;
		case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
			this.getAllSatellites();
			break;
		}
	}
	//private float lastSpeed = 0;
	
	@Override
	public void onLocationChanged(final Location location) {
		if (location != null) {
			Log.v("gpsd", "onLocationChanged callback was called!");
			
			if (this.mTimeStampMS != -1) {
				// in meters per second
				this.mDiffAgeData = location.getTime() - this.mTimeStampMS;
				this.mRateOfClimb = (float) (this.mLastAltitude - location.getAltitude()) * 1000.0f / this.mDiffAgeData;
								
				if(!location.hasBearing()){
					float bearing = this.mLocation.bearingTo(location);
					this.mLocation.setBearing(bearing);
				}
				/*
				if(location.hasSpeed()){
					lastSpeed = location.getSpeed(); 
				} else {
					location.setSpeed(lastSpeed);
				}*/
			}
			this.mLocation = location;
			
			this.mTimeStampMS = location.getTime();
			this.mTimeStampUp = this.mTimeStampMS / 1000;
			this.mTimeStampLow = ((int) (this.mTimeStampMS % 1000)) / 10;
			this.mLastAltitude = location.getAltitude();
			this.mLastAltitude = location.getAltitude();
			
			sendLocChangeToClient();
		} else {
			Log.v("gpsd", "onLocationChanged callback was called with NULL Location!!");
		}
	}

	public void sendLocChangeToClient() {
		Log.v(this.toString(),"================Send Loc Change to Client called!============");
		if(mCallbacks==null){
			Log.e(this.toString(),"Error: mCallbacks was null!");
			return;
		}
		
	    synchronized (mCallbacks) {
			int N = mCallbacks.beginBroadcast();
			if(N > 0) { // its imortant to call finishBroadcast even if N == 0!
				if(this.mLocation == null ){
					Log.e(this.toString(),"Error, ==> location == null, sendLocChangeToClient called to soon ?");
					return;
				}
				String send_str = this.mLocation.toString();
			    for (int i=0; i<N; i++) {
			        try {
			        	Log.v(this.toString(),"sending : "+send_str);
			            mCallbacks.getBroadcastItem(i).gpsChanged(send_str);
			        } catch (RemoteException e) {
			            // The RemoteCallbackList will take care of removing
			            // the dead object for us.
			        	Log.d(this.toString(),"RemoteException occured!");
			        }
			    }
			}
		    mCallbacks.finishBroadcast();
	    }
	}

	public void onProviderDisabled(final String provider) {
	}

	public void onProviderEnabled(final String provider) {
	}

	@Override
	public void onStatusChanged(final String provider, final int status,
			final Bundle extras) {
		// TODO Auto-generated method stub
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
		return "GPSD,Q=" + this.mNumSatellites + " ? ? ? ? ? ";
		// return "GPSD,Q=6 17.37 2.40 12.78 14.37 22.54";
	}

	public void requestExitAndWait() {
		this.mDone = true;
	    // Unregister all callbacks.
	    mCallbacks.kill();
		
		for (int i = 0; i < this.mThreadList.size(); i++) {
			this.mThreadList.get(i).requestExitAndWait();
			Log.d(this.toString(), "Thread Nr " + i + 1 + " closed now!");
		}
		// Tell the thread to quit
		try {
			if(mServerSocket!=null) mServerSocket.close();
			this.join();
		} catch (final InterruptedException ex) {
			// Ignore
		} catch (IOException e) {
			Log.i(this.toString(),"Error on server socket close!");
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		// first open the server socket!
		Log.d(this.toString(), "Worker Thread Started!");
		try {
			mServerSocket = new ServerSocket(this.mServerPort);
			mServerSocket.setSoTimeout(Constants.SOCKET_TIMEOUT); // send timeout
			// ?!
		} catch (final Exception e) {
			Log.e("gpsd", "Error on Server Socket Connect!:");
			e.printStackTrace();
			return;
		}
		Log.d(this.toString(), "Server Socket UP and Rolling!");
		while (!this.mDone) {
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
			Log.d(this.toString(), "Client got connected on Port: " + this.mServerPort);
			final EchoThread thread = new EchoThread(clientSocket, this);
			this.mThreadList.add(thread);
			thread.start();
		}
		Log.d(this.toString(), "Callback Thread Done!");
	}

	public String sendXMode(final boolean send_i) {
		if (send_i) {
			return ",X=" + this.mTimeStampUp + "." + this.mTimeStampLow
					+ ",I=Generic NMEA";
		}
		return ",X=" + this.mTimeStampUp + "." + this.mTimeStampLow;
	}
}
