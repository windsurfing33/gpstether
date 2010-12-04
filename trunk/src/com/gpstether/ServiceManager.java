package com.gpstether;

import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.gpstether.service.ITetherService;
import com.gpstether.service.ITetherServiceCallback;

public final class ServiceManager {
	/********************************************************************************************************/
	/**
	 *  Service CONNECT
	 */
	private final class tetherServiceConnection implements ServiceConnection {
		public UpdateStatus mStatusIf = null;

		@Override
		public void onServiceConnected(final ComponentName className,
				final IBinder boundService) {
			tetherService = ITetherService.Stub.asInterface(boundService);
			try {
				tetherService.registerCallback(mCallback);
			} catch (final RemoteException e) {
				e.printStackTrace();
			}
			Log.d(getClass().toString(), "===> onServiceConnected");
		}

		@Override
		public void onServiceDisconnected(final ComponentName className) {
			try {
				tetherService.unregisterCallback(mCallback);
			} catch (final RemoteException e) {
				e.printStackTrace();
			}
			tetherService = null;
			Log.d(toString(), "onServiceDisconnected");
			updateServiceStatus(mStatusIf);
		}

		public void SetUpdateStatusIf(final UpdateStatus statusIf) {
			mStatusIf = statusIf;
			Log.d(getClass().toString(), "===> SetUpdateStatusIf");
		}
	}
	/********************************************************************************************************/
	private tetherServiceConnection conn;
	protected static String className = "com.gpstether.service.TetherService";
	protected static final int GPS_MSG = 0;
	protected static String packageNameSpace = "com.gpstether";

	private final Context mCtx;
	private boolean started = false;
	private ITetherService tetherService;
	
	/**
	 * Interface for client activity!
	 */
	public interface UpdateStatus {
		public void updateGPSStatus(String status);

		public void updateServiceStatus(String status);
	}
	
	public static boolean findServiceInTaskList(final Context act) {
		final ActivityManager activityManager = (ActivityManager) act.getSystemService(Context.ACTIVITY_SERVICE);
		final List<ActivityManager.RunningServiceInfo> runningTasks = activityManager.getRunningServices(Integer.MAX_VALUE);
		Log.v("gpstether", "======================");
		for (final RunningServiceInfo serviceInfo : runningTasks) {
			final ComponentName serviceName = serviceInfo.service;
			Log.v("gpstether", "SERVICE NAME: " + serviceName);
			if (serviceName.getClassName().equals(ServiceManager.className)){
				return true;
			}
		}
		return false;
	}

	/**
	 * This implementation is used to receive callbacks from the remote service.
	 */
	private final ITetherServiceCallback mCallback = new ITetherServiceCallback.Stub() {
		/**
		 * This is called by the remote service regularly to tell us about new
		 * values. Note that IPC calls are dispatched through a thread pool
		 * running in each process, so the code executing here will NOT be
		 * running in our main thread like most other things -- so, to update
		 * the UI, we need to use a Handler to hop over there.
		 */
		public void gpsChanged(final String str) {
			mHandler.sendMessage(mHandler.obtainMessage(ServiceManager.GPS_MSG, str));
		}
	};

	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(final Message msg) {
			switch (msg.what) {
			case GPS_MSG:
				final String msg_data = (String) msg.obj;
				((UpdateStatus) mCtx).updateGPSStatus(msg_data);
				break;
			default:
				super.handleMessage(msg);
			}
		}
	};

	public ServiceManager(final Context ctx) {
		mCtx = ctx;
	}

	private boolean bindActToService() {
		if (conn == null) {
			conn = new tetherServiceConnection();
			conn.SetUpdateStatusIf((UpdateStatus) mCtx);

			mCtx.bindService(createServiceIntent(), conn,
					Context.BIND_AUTO_CREATE);

			Log.d(ServiceManager.this.toString(), "bindService()");
			updateServiceStatus((UpdateStatus) mCtx);
			return true;
		}
		return false;
	}

	private Intent createServiceIntent() {
		final Intent i = new Intent();
		i.setClassName(ServiceManager.packageNameSpace,
					   ServiceManager.className);
		return i;
	}

	@Override
	protected void finalize() {
		unbindActFromService();
	}

	public boolean StartService() {
		if (!started) {
			// first start the service!
			mCtx.startService(createServiceIntent());
			Log.d(ServiceManager.this.toString(), "startService()");
			started = true;
			updateServiceStatus((UpdateStatus) mCtx);
		}
		if (!bindActToService()) {
			Toast.makeText(mCtx, "Service could not be bound!",
					Toast.LENGTH_SHORT).show();
		}
		if (conn != null && started) {
			Toast.makeText(mCtx, "Service started and bound !",
					Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(mCtx, "Service is not started and not bound!",
					Toast.LENGTH_SHORT).show();
		}
		return started;
	}

	public void StopService() {
		// first unbind client activity from the service!
		unbindActFromService();
		// now stop the service!
		if (!started) {
			Toast.makeText(mCtx, "Service was not started", Toast.LENGTH_SHORT)
					.show();
		} else {
			mCtx.stopService(createServiceIntent());
			Log.d(ServiceManager.this.toString(), "stopService()");
			started = false;
		}
		Toast notification = null;
		if (conn == null && !started) {
			notification = Toast.makeText(mCtx,
					R.string.service_stopped_and_unbound, Toast.LENGTH_LONG);
		} else if (conn == null) {
			notification = Toast.makeText(mCtx,
					R.string.service_unbound_stop_pending, Toast.LENGTH_LONG);
		} else if (!started) {
			notification = Toast
					.makeText(mCtx, R.string.service_stopped_unbound_pending,
							Toast.LENGTH_LONG);
		} else {
			notification = Toast.makeText(mCtx,
					R.string.service_stop_and_unbound_pending,
					Toast.LENGTH_LONG);
		}
		notification.show();
		updateServiceStatus((UpdateStatus) mCtx);
	};

	private boolean unbindActFromService() {
		if (mCtx != null && conn != null) {
			mCtx.unbindService(conn);
			conn = null;
			Log.d(ServiceManager.this.toString(), "unbindActFromService()");
			return true;
		}
		return false;
	}

	private void updateServiceStatus(final UpdateStatus act) {
		if (act == null) {
			Log.e(toString(), "Error: update status interface is null");
			return;
		}
		
		final String bindStatus  = conn == null ? "unbound" : "bound";
		final String startStatus = started ? "started" : "not started";
		final String statusText  = "Service status: " + bindStatus + "," + startStatus;
		
		act.updateServiceStatus(statusText);
	}
}
