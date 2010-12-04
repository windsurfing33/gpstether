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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import android.util.Log;

public class EchoThread extends Thread {

	private String  mDeviceName = Constants.GPSD_DEVICE_NAME;
	
	private CallbackThread mCB = null;
	private Socket mClientSocket = null;
	
	private boolean mDone 			= false;
	private boolean mRawMode 		= false;
	private boolean mWatcherMode 	= false;
	private boolean mXSend 			= false;
	
	public EchoThread(final Socket s, final CallbackThread cb) {
		super();
		mCB = cb; mClientSocket = s;	
	}

	/*
	 * every gpsd reply will start with the string "GPSD" followed by the
	 * replies. Examples: query: "p\n" reply: "GPSD,P=36.000000 123.000000\r\n"
	 * query: "d\n" reply: "GPSD,D=2002-11-16T02:45:05.12Z\r\n" query: "va\n"
	 * reply: "GPSD,V=0.000000,A=37.900000\r\n"
	 */
	private String getReplyString(final String str) {
		String reply = Constants.REPLY_START;

		for (int idx = 0; idx < str.length(); idx++) {
			final char c = str.toLowerCase().charAt(idx);
			switch (c) {
			case 'a': reply += mCB.getAltitude(); 				 break;
			case 'p': reply += mCB.getPosition(); 		   		 break;
			case 'o': reply += mCB.getNavInfo(); 		   		 break;
			case 'd': reply += mCB.getUTCTime();		   		 break;
			case 'v': reply += mCB.getLastKnownLocation(); 		 break;
			case 'u': reply += mCB.getRateOfClimb();       		 break;
			case 't': reply += mCB.getBearing(); 		   		 break;
			case 'x': reply += mCB.sendXMode(false);       		 break;
			case 'q': reply += mCB.printOutSatellites();   		 break;
			case 'e': reply += "E=? ? ?"; 				  		 break;
			case 'i': reply += ",I=" + mDeviceName.substring(2); break;
			// * we only have one device so this does nothing !
			// * uses old or fallback name if empty command was given!
			case 'f':
				if ((idx + 1 < str.length()) && (str.charAt(idx + 1) == '=')) {
					   // COPY DEVICE NAME!
					   mDeviceName = str.substring(idx);
					   Log.v(getClass().toString(), "NEW DEVICE NAME SET <"+ mDeviceName +">");
				} else Log.v(getClass().toString(), "DEVICE NAME STILL IS <"+ mDeviceName +">");
				return mDeviceName;
			case 'w':
				if (idx + 1 < str.length()) {
					switch (test_true_false_unknown(str, idx+1,false)) {
						case Constants.FALSE: mWatcherMode = false; reply += ",W=0"; break;
						case Constants.TRUE:  
							mWatcherMode = true; reply += mXSend?",W=1":
							(mCB.sendXMode(true) + Constants.COMMAND_END+ Constants.REPLY_START);
							mXSend = true; break;
						case Constants.UNKNOWN: return null; // unknown command!
					}
					idx++;
				} else {
					reply += mWatcherMode?",W=0":",W=1"; mWatcherMode = !mWatcherMode;
				}
				break;
			case 'r':
				if (idx + 2 < str.length()) {
					switch (test_true_false_unknown(str, idx+2,true)) {
						case Constants.FALSE: mRawMode = false; reply += ",R=0"; break;
						case Constants.TRUE:  
							mRawMode = true;
							reply += mXSend ? ",R=1" : 
								(mCB.sendXMode(true) + Constants.COMMAND_END + Constants.REPLY_START);
							mXSend = true; break;
						case Constants.UNKNOWN: return null; // unknown command!
					}
					idx++;
				} else {
					if (mRawMode) {
						reply += ",R=0";
					} else {
						reply += ",R=1";
					}
					mRawMode = !mRawMode;
				}
				break;
			default: return null; /* unknown !*/ 
			}
		}
		return reply;
	}

	private int test_true_false_unknown(final String str, final int i, final boolean rb) {
		if(i < str.length() ) switch (str.charAt(i)) {
			case '0':
			case '-': return Constants.FALSE;
			case '1':
			case '2': if(!rb) return Constants.UNKNOWN;
			case '+': return Constants.TRUE;
		} else Log.e(getClass().toString(), "test_true_false_unknown: detected idx >= string length!");
		return Constants.UNKNOWN;
	}

	public void requestExitAndWait() {
		// Tell the thread to quit
		mDone = true;
		try {
			this.join();
		} catch (final InterruptedException ex) {
			Log.e(toString(), "requestExitAndWait() InterruptedException:");
			ex.printStackTrace();
		}
	}

	private void resetState() {
		mWatcherMode = false;
		mXSend = false;
	}

	@Override
	public void run() {
		boolean fatal = false;
		if (mClientSocket == null) {
			Log.e(toString(), "No Client Socket found!");
			return;
		}
		while (!mDone && !fatal) {
			try { // catch all fatal exceptions!
				mClientSocket.setSoTimeout(Constants.SOCKET_TIMEOUT);

				final BufferedReader in = new BufferedReader(
						new InputStreamReader(mClientSocket.getInputStream()));
				final BufferedWriter out = new BufferedWriter(
						new OutputStreamWriter(mClientSocket.getOutputStream()));
				String str;
				while (!mDone && mClientSocket.isBound()) {
					str = null;
					try { // wait for timeout!
						if (!mWatcherMode && !mRawMode || in.ready()) {
							str = in.readLine();
						}
					} catch (final SocketTimeoutException e) { // catch only
						// read
						// timeouts here!!
						// Log.d(this.toString(),"input readline timeout");
						if (!mWatcherMode && !mRawMode) {
							continue;
						}
						str = null;
					} catch (final SocketException e) {
						break;
					}
					if (str != null) {
						Log.v("gpsd", "gpsd got : " + str);
					}
					if (str == null) {
						// String reply_str = printOutSatellites();
						// Log.d("gspd","Watching Mode ==> Reply String: \""+reply_str+"\"");
						String reply_str = new String();
						if (mWatcherMode) {
							reply_str += Constants.REPLY_START
									+ mCB.getNavInfo();
						}
						if (mRawMode) {
							reply_str += mCB.getRawInfo();
						}
						// Log.d("gspd","Watching Mode ==> Reply String: \""+reply_str+"\"");
						Thread.sleep(1000);
						try {
							out.append(reply_str + Constants.COMMAND_END);
							out.flush();
							// reply_str = printOutSatellites();
							// out.append(reply_str+COMMAND_END);
							// out.flush();
						} catch (final SocketException e) {
							break;
						}// client got disconnected!!!
					} else { // does some sleep and check for disconnect!
						final String reply_str = getReplyString(str);
						if (reply_str != null) {
							// Log.d("gspd"," ==> Reply String: \""+reply_str+"\"");
							out.append(reply_str + Constants.COMMAND_END);
							out.flush();
						} else {
							Log.e(toString(), "Unknown Command: " + str);
						}
					}
				}
				resetState();
				Log.v("gspd", "Client got disconnected");
				out.close();
				in.close();
			} catch (final Exception e) {
				e.printStackTrace();
				fatal = true;
			}
			if (mClientSocket != null) {
				try {
					mClientSocket.close();
				} catch (final IOException e) {
					Log.e(toString(), "Client Socket cannot be closed!");
					e.printStackTrace();
					break;
				}
				Log.d(toString(), "Client Socket closed now!");
				mClientSocket = null;
			} else {
				fatal = true;
			}
			if (fatal) {
				Log.e("gspd","Fatal Error! I exit the Thread now because of this error!");
			}
		}
		Log.d(toString(), "Client thread exits! "
				+ (fatal ? "with fatal error" : " successfully"));
	}
}
