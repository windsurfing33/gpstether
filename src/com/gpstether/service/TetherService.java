package com.gpstether.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.gpstether.R;

public class TetherService extends Service {

	/**
	 * The IAdderService is defined through IDL
	 */
	private final ITetherService.Stub binder = new ITetherService.Stub() {
		@Override
		public void registerCallback(final ITetherServiceCallback cb) throws RemoteException {

			if (cb != null) {
				mCallbacks.register(cb);
			} else {
				Log.e(toString(), "Error, cannot register a null callback!");
			}
		}

		@Override
		public void unregisterCallback(final ITetherServiceCallback cb)
				throws RemoteException {
			if (cb != null) {
				mCallbacks.unregister(cb);
			}
		}
	};
	private CallbackThread mCbThread = null;
	private final RemoteCallbackList<ITetherServiceCallback> mCallbacks = new RemoteCallbackList<ITetherServiceCallback>();
	private LocationManager mLocMan = null;

	@Override
	public IBinder onBind(final Intent intent) {
		return binder;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.d(getClass().toString(), "onCreate");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mCbThread != null) {
			Log.d(getClass().toString(), "removing GPS Status listener !");
			mLocMan.removeUpdates(mCbThread);
			mLocMan.removeGpsStatusListener(mCbThread);
			mLocMan = null;
			mCbThread.requestExitAndWait();
		}
		Toast.makeText(TetherService.this, R.string.service_stopped,
				Toast.LENGTH_LONG).show();
	}

	@Override
	public void onStart(final Intent intent, final int startId) {
		super.onStart(intent, startId);

		final int default_server_port = Integer.valueOf(this
				.getString(R.string.default_server_port));

		Log.d(getClass().toString(),
				"==========> onStart fires server on port: "+ default_server_port);
	
		mLocMan = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		mCbThread = new CallbackThread(default_server_port, mLocMan, mCallbacks);

		mLocMan.addGpsStatusListener(mCbThread);
		mLocMan.requestLocationUpdates(LocationManager.GPS_PROVIDER,
									   Constants.GPS_UPDATE_MS_TH,
									   Constants.GPS_UPDATE_METERS_TH, mCbThread);
		
		mLocMan.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
									   Constants.NETW_UPDATE_MS_TH, 
									   Constants.NETW_UPDATE_METERS_TH, mCbThread);
		
		mCbThread.start();
		// Tell the user we have started.
		Toast.makeText(TetherService.this, R.string.service_started,
				Toast.LENGTH_SHORT).show();
	}
}
