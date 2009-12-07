package com.gpstether;
	 
import java.util.Iterator;
import java.util.List;

import android.app.ActivityManager;
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
	protected static String className = "com.gpstether.service.TetherService";
	protected static String packageNameSpace = "com.gpstether";
	
	protected static final int GPS_MSG = 0;
	private tetherServiceConnection conn;
	private boolean started = false;
    private Context mCtx;
    
	private ITetherService tetherService;
	/**
     * This implementation is used to receive callbacks from the remote
     * service.
     */
    private ITetherServiceCallback mCallback = new ITetherServiceCallback.Stub() {
        /**
         * This is called by the remote service regularly to tell us about
         * new values.  Note that IPC calls are dispatched through a thread
         * pool running in each process, so the code executing here will
         * NOT be running in our main thread like most other things -- so,
         * to update the UI, we need to use a Handler to hop over there.
         */
        public void gpsChanged(String str) {
            mHandler.sendMessage(mHandler.obtainMessage(GPS_MSG, str));
        }
    };

	
	public ServiceManager(Context ctx){
		this.mCtx = ctx;
	//	if(!bindActToService()) Log.e(this.toString(),"Error: cannot bind service!");
	}
	
	protected void finalize ()  {
		unbindActFromService();
    }
	
	private boolean unbindActFromService() {
		if((mCtx != null) && (this.conn != null)) {
			mCtx.unbindService(this.conn);
			this.conn = null;
			Log.d(ServiceManager.this.toString(), "unbindService()");
			return true;
		} 
		return false;
	}

	class tetherServiceConnection implements ServiceConnection {
		public UpdateStatus mStatusIf = null;
		@Override
		public void onServiceConnected( ComponentName className, IBinder boundService) {
			ServiceManager.this.tetherService = ITetherService.Stub.asInterface(boundService);
    	    try {
				ServiceManager.this.tetherService.registerCallback(ServiceManager.this.mCallback);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			Log.d(this.toString(), "onServiceConnected");
		}
		@Override
		public void onServiceDisconnected(ComponentName className) {
			try {
				ServiceManager.this.tetherService.unregisterCallback(ServiceManager.this.mCallback);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			ServiceManager.this.tetherService = null;
			Log.d(this.toString(), "onServiceDisconnected");
			ServiceManager.this.updateServiceStatus(this.mStatusIf);
		}
		public void SetUpdateStatusIf(UpdateStatus statusIf) {
			this.mStatusIf = statusIf;
			Log.d(this.toString(), "SetUpdateStatusIf");
		}
	}

	/*
	 * Interface for client activity!
	 */
	public interface UpdateStatus {
		public void updateGPSStatus(String status);
		public void updateServiceStatus(String status);
	}

	private Intent createServiceIntent()
	{
		Intent i = new Intent();
		i.setClassName(packageNameSpace, className);
		return i;
	}
	
	public boolean StartService() {
		if(!this.started) {
			// first start the service!
			mCtx.startService(createServiceIntent());
			Log.d(ServiceManager.this.toString(), "startService()");
			this.started = true;
			this.updateServiceStatus((UpdateStatus) mCtx);
		}
		if(!bindActToService()){
			Toast.makeText(mCtx, "Service could not be bound!", Toast.LENGTH_SHORT).show();
		}
		if( this.conn != null && this.started) {
			Toast.makeText(mCtx, "Service started and bound !", Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(mCtx, "Service is not started and not bound!", Toast.LENGTH_SHORT).show();
		}
		return this.started;
	}

	private boolean bindActToService() {
		if (this.conn == null) {
			this.conn = new tetherServiceConnection();
			this.conn.SetUpdateStatusIf((UpdateStatus) mCtx);
			
			mCtx.bindService(createServiceIntent(), this.conn, Context.BIND_AUTO_CREATE);
			
			Log.d(ServiceManager.this.toString(), "bindService()");
			
			this.updateServiceStatus((UpdateStatus) mCtx);
			return true;
		}
		return false;
	}

	public void StopService() {
		// first unbind client activity from the service!
		unbindActFromService();

		// now stop the service!
		if (!this.started) {
			Toast.makeText(mCtx, "Service was not started", Toast.LENGTH_SHORT).show();
		} else {
			mCtx.stopService(createServiceIntent());
			Log.d(ServiceManager.this.toString(), "stopService()");
			this.started = false;
		}
		if(this.conn == null && !this.started) {
			Toast.makeText(mCtx, "Service stopped and unbound!", Toast.LENGTH_SHORT).show();
		} else if(this.conn == null) {
			Toast.makeText(mCtx, "Service unbound but not stopped!", Toast.LENGTH_SHORT).show();
		} else if(!this.started) {
			Toast.makeText(mCtx, "Service stopped but not unbound!", Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(mCtx, "Service not stopped and not unbound!", Toast.LENGTH_SHORT).show();
		}
		this.updateServiceStatus((UpdateStatus) mCtx);
	}

	private void updateServiceStatus( UpdateStatus act) {
		if(act == null){
			Log.e(this.toString(), "Error: update status interface is null");
			return;
		}
		final String bindStatus = this.conn == null ? "unbound" : "bound";
		final String startStatus = this.started ? "started" : "not started";
		final String statusText = "Service status: " + bindStatus + "," + startStatus;
		act.updateServiceStatus(statusText);
	};
	
	private Handler mHandler = new Handler() {
	        public void handleMessage(Message msg) {
	            switch (msg.what) {
	                case GPS_MSG:
	                	String msg_data = (String)msg.obj;
	                	((UpdateStatus) mCtx).updateGPSStatus(msg_data);
	                    break;
	                default:
	                    super.handleMessage(msg);
	            }
	        }
     };
     
	 public static boolean findServiceInTaskList(Context act) {
	        ActivityManager activityManager = (ActivityManager)act.getSystemService(Context.ACTIVITY_SERVICE);
			List<ActivityManager.RunningServiceInfo> runningTasks = activityManager.getRunningServices(Integer.MAX_VALUE);
			
			for (Iterator<ActivityManager.RunningServiceInfo> iterator = runningTasks.iterator(); iterator.hasNext();) {
				ActivityManager.RunningServiceInfo serviceInfo = (ActivityManager.RunningServiceInfo) iterator.next();
				ComponentName serviceName = serviceInfo.service;
				
			//    Log.v(act.toString(),"Service class: " + (i++) + " : " + serviceName.getClassName());
			    if(serviceName.getClassName().equals(className))  return true;
			}
			return false;
	  }
}
