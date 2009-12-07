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
package com.gpstether;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class GPSTether extends Activity implements Eula.OnEulaAgreedTo, ServiceManager.UpdateStatus {

	protected static final int CHECKSERVICE = 0;

	private  TextView mServiceStatus;
	private  TextView mGPSStatus;
	
	private static final int UPDATE_DELAY_MS = 1000;

	void startPollingServiceUpdate() { 
		final Handler handler = new Handler();
		final Runnable updateCaller = new Runnable() {
			public void run() {
				boolean is_running = ServiceManager.findServiceInTaskList(GPSTether.this);
				//Log.i( this.toString(), "Service Polling:" +( is_running ? "is up and running" : "is not running!") );

				Button stopServiceButton = (Button) findViewById(R.id.stopservice );
			    Button startServiceButton = (Button) findViewById(R.id.startservice );

			    startServiceButton.setEnabled(!is_running);
			    stopServiceButton.setEnabled(is_running);
			    
				handler.postDelayed( this, UPDATE_DELAY_MS );
			}
		};
    	// kick it off first time!
		handler.postDelayed( updateCaller, UPDATE_DELAY_MS );
	}
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		Log.i(this.toString(),"====>Resumed");
		if (Eula.show(this)) {
			this.onEulaAgreedTo();
		}
	}
	@Override
	public void onPause() {
		super.onPause();
		Log.i(this.toString(),"====>Pause");
	}
	
	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		menu.add("Close");
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onEulaAgreedTo() {
		
		final String gps_na_str = this.getString(R.string.gpsstatus);

		// startup and init service manager		
		final ServiceManager service_manager = new ServiceManager(this);
		
		setContentView(R.layout.main);
		
	    mServiceStatus = (TextView) this.findViewById(R.id.servicestatus);
	    mGPSStatus = (TextView) this.findViewById(R.id.gpsStatus);
		
		Button startServiceButton = (Button) findViewById(R.id.startservice );
		startServiceButton.setOnClickListener(new View.OnClickListener() {
		    public void onClick(View view) {
		    	service_manager.StartService();
		    }
		});
		Button stopServiceButton = (Button) findViewById(R.id.stopservice );
		stopServiceButton.setOnClickListener(new View.OnClickListener() {
		    public void onClick(View view) {
		    	service_manager.StopService();
		    	mGPSStatus.setText(gps_na_str);
	     	}
		});
	    startServiceButton.setEnabled(false);
	    stopServiceButton.setEnabled(false);

		startPollingServiceUpdate();
    }
	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		super.onOptionsItemSelected(item);
		switch (item.getItemId()) {
			case 0: this.finish();
		}
		return false;
	}
	
	@Override
	public void updateGPSStatus(String status) {
		if(status != null) mGPSStatus.setText(status);
	}

	@Override
	public void updateServiceStatus( String status) {
		if(status != null) mServiceStatus.setText(status);
	}

}
