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
		
		private CallbackThread cb_thread = null;
		private LocationManager mLocMan = null;
		private final RemoteCallbackList<ITetherServiceCallback> mCallbacks = new RemoteCallbackList<ITetherServiceCallback>();
		
	    @Override
	    public void onStart( Intent intent, int startId ) {
		  super.onStart( intent, startId );

		  int default_server_port = Integer.valueOf(this.getString(R.string.default_server_port));
		  
	      Log.d( this.toString(), "==========> onStart fires server on port: "+default_server_port);
	      mLocMan = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
	      cb_thread = new CallbackThread(default_server_port, mLocMan , mCallbacks);
	      
	      mLocMan.addGpsStatusListener(cb_thread);
	      mLocMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, Constants.gpsUpdateTimeMsec, Constants.gpsUpdateThresholdInMeters, this.cb_thread);
	      mLocMan.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, Constants.gpsUpdateTimeMsec, 500.0f, this.cb_thread);
  	      cb_thread.start();
		// Tell the user we have started.
	      Toast.makeText(TetherService.this, R.string.service_started, Toast.LENGTH_SHORT).show();
	    }

	    @Override
	    public IBinder onBind( Intent intent ) {
	        return binder;
	    }
	    
	    @Override
	    public void onCreate() {
	        super.onCreate();
		    Log.d( this.toString(),"onCreate" );
	    }

	    @Override
	    public void onDestroy() {
	    	super.onDestroy();
	    	if(cb_thread != null) {
	    	   Log.d(this.toString(),"removing GPS Status listener !");
	    	   mLocMan.removeUpdates(cb_thread); 
	    	   mLocMan.removeGpsStatusListener(cb_thread);
	    	   mLocMan = null;
		       cb_thread.requestExitAndWait();
	    	}
	        Toast.makeText(TetherService.this, R.string.service_stopped, Toast.LENGTH_LONG).show();
	    }
	    /**
	     * The IAdderService is defined through IDL
	     */
	    private final ITetherService.Stub binder = new ITetherService.Stub() {
			@Override
			public void registerCallback(ITetherServiceCallback cb) throws RemoteException {
				   Log.v(this.toString(),"===================================================================");
				   if (cb != null) mCallbacks.register(cb);
				   else Log.e(this.toString(),"Error, cannot register a null callback!");
			}
			@Override
			public void unregisterCallback(ITetherServiceCallback cb) throws RemoteException {
				  if(cb != null) mCallbacks.unregister(cb);
			}
	    };
	}
