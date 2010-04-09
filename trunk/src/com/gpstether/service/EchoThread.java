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

	private CallbackThread mCB = null;

	private Socket mClientSocket = null;
	private String mDeviceName = "F,Android GPS Device";

	private boolean mDone = false;

	// private float mTimeError = 0.005f;
	private boolean mWatcherMode = false;
	private boolean mRawMode = false;

	private boolean mXSend = false;

	public EchoThread(final Socket s, final CallbackThread cb) {
		super();
		this.mCB = cb;
		this.mClientSocket = s;
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
			case 'p':
				reply += this.mCB.getPosition();
				break;
			case 'o':
				reply += this.mCB.getNavInfo();
				break;
			case 'd':
				reply += this.mCB.getUTCTime();
				break;
			// 1 Meter per Second = 1.9438444924406 Knots
			case 'v':
				reply += this.mCB.getLastKnownLocation();
				break;
			case 'f': // we only have one device so this does nothing !
				if (idx + 1 < str.length()) {
					if (str.charAt(idx + 1) == '=') {
						// COPY DEVICE NAME!
						this.mDeviceName = "";
						for (; idx < str.length(); idx++) {
							this.mDeviceName += str.charAt(idx);
						}
					}
				}
				reply += this.mDeviceName;
				break;
			case 'i':
				reply += ",I=" + this.mDeviceName.substring(2);
				break;
			case 'u':
				reply += this.mCB.getRateOfClimb();// rate of climb
				break;
			case 'w':
				if (idx + 1 < str.length()) {
					switch (str.charAt(idx + 1)) {
					case '0':
					case '-':
						this.mWatcherMode = false;
						reply += ",W=0";
						break;
					case '1':
					case '+':
						if (!this.mXSend) {
							reply += this.mCB.sendXMode(true) + Constants.COMMAND_END + Constants.REPLY_START;
							this.mXSend = true;
						}
						this.mWatcherMode = true;
						reply += ",W=1";
						break;
					default:
						return null;
					}
					idx++;
				} else {
					if (this.mWatcherMode) {
						reply += ",W=0";
					} else {
						reply += ",W=1";
					}
					this.mWatcherMode = !this.mWatcherMode;
				}
				break;
			case 'e':
				reply += "E=? ? ?";
				break;
			case 't':
				reply += this.mCB.getBearing();
				break;
			case 'x':
				reply += this.mCB.sendXMode(false);
				break;
			case 'q':
				reply += this.mCB.printOutSatellites();
				break;
			case 'r':
				if (idx + 1 < str.length()) {
					switch (str.charAt(idx + 2)) {
					case '0':
					case '-':
						mRawMode = false;
						reply += ",R=0";
						break;
					case '1':
					case '2':
					case '+':
						if (!mXSend) {
							reply += mCB.sendXMode(true) + Constants.COMMAND_END + Constants.REPLY_START;
							mXSend = true;
						}
						mRawMode = true;
						reply += ",R=1";
						break;
					default:
						return null;
					}
					idx++;
				} else {
					if (mRawMode)
						reply += ",R=0";
					else
						reply += ",R=1";
					mRawMode = !mRawMode;
				}
				break;
			case 'a':
				reply += this.mCB.getAltitude();
				break;
			default:
				return null;
			}
		}
		return reply;
	}

	public void requestExitAndWait() {
		// Tell the thread to quit
		this.mDone = true;
		try {
			this.join();
		} catch (final InterruptedException ex) {
			Log.e(this.toString(), "requestExitAndWait() InterruptedException:");
			ex.printStackTrace();
		}
	}

	private void resetState() {
		this.mWatcherMode = false;
		this.mXSend = false;
	}

	@Override
	public void run() {
		boolean fatal = false;
		if (this.mClientSocket == null) {
			Log.e(this.toString(), "No Client Socket found!");
			return;
		}
		while (!this.mDone && !fatal) {
			try { // catch all fatal exceptions!
				this.mClientSocket.setSoTimeout(Constants.SOCKET_TIMEOUT);

				final BufferedReader in = new BufferedReader(
						new InputStreamReader(this.mClientSocket
								.getInputStream()));
				final BufferedWriter out = new BufferedWriter(
						new OutputStreamWriter(this.mClientSocket
								.getOutputStream()));
				String str;
				while (!this.mDone && this.mClientSocket.isBound()) {
					str = null;
					try { // wait for timeout!
						if ((!this.mWatcherMode && !this.mRawMode) || in.ready()) {
							str = in.readLine();
						}
					} catch (final SocketTimeoutException e) { // catch only
																// read
						// timeouts here!!
						// Log.d(this.toString(),"input readline timeout");
						if (!this.mWatcherMode && !this.mRawMode) {
							continue;
						}
						str = null;
					} catch (final SocketException e) {
						break;
					}
					if (str != null)
						Log.v("gpsd","gpsd got : "+str);
					if (str == null) {
						// String reply_str = printOutSatellites();
						// Log.d("gspd","Watching Mode ==> Reply String: \""+reply_str+"\"");
						String reply_str = new String();
						if (this.mWatcherMode)
							reply_str += Constants.REPLY_START
								+ this.mCB.getNavInfo();
						if (this.mRawMode)
							reply_str += this.mCB.getRawInfo();
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
						final String reply_str = this.getReplyString(str);
						if (reply_str != null) {
							// Log.d("gspd"," ==> Reply String: \""+reply_str+"\"");
							out.append(reply_str + Constants.COMMAND_END);
							out.flush();
						} else {
							Log.e(this.toString(), "Unknown Command: " + str);
						}
					}
				}
				this.resetState();
				Log.v("gspd", "Client got disconnected");
				out.close();
				in.close();
			} catch (final Exception e) {
				e.printStackTrace();
				fatal = true;
			}
			if (this.mClientSocket != null) {
				try {
					this.mClientSocket.close();
				} catch (final IOException e) {
					Log.e(this.toString(), "Client Socket cannot be closed!");
					e.printStackTrace();
					break;
				}
				Log.d(this.toString(), "Client Socket closed now!");
				this.mClientSocket = null;
			} else {
				fatal = true;
			}
			if (fatal) {
				Log.e("gspd","Fatal Error! I exit the Thread now because of this error!");
			}
		}
		Log.d(this.toString(), "Client thread exits! " + (fatal ? "with fatal error" : " successfully"));
	}
}
