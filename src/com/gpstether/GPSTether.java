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

public class GPSTether extends Activity implements Eula.OnEulaAgreedTo,
		ServiceManager.UpdateStatus {

	protected static final int CHECKSERVICE 	= 0;
	private   static final int CLOSE_MENU 		= 0;
	
	private   static final int POLL_UPDATE_DELAY_MS 	= 1000;

	private TextView mGPSStatus,mServiceStatus;
	
	private ServiceManager mServiceMan = null;
	
	private final Handler status_poll_handler = new Handler();
	private Runnable status_poll_runnable = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		menu.add("Close");
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	protected void onDestroy() {
		Log.i(toString(), "====>Destroy");
		tryRemovePollingCallbacks();
		super.onDestroy();
	}

	private View.OnClickListener mOCL = new View.OnClickListener() {
		public void onClick(final View v) {
			switch(v.getId()){
			case R.id.startservice:
				mServiceMan.StartService(); break;
			case R.id.stopservice:
				mServiceMan.StopService();
				mGPSStatus.setText( getString(R.string.gpsstatus) );break;
			}
		}
	};
	
	@Override
	public void onEulaAgreedTo() {

		//if(!(mServiceMan instanceof ServiceManager)) 
			
		mServiceMan = new ServiceManager(this);
		setContentView(R.layout.main);

		mServiceStatus = (TextView) findViewById(R.id.servicestatus);
		mGPSStatus = (TextView) findViewById(R.id.gpsStatus);

		
		Button startServiceButton = (Button) findViewById(R.id.startservice);
		Button stopServiceButton = ((Button) findViewById(R.id.stopservice));
		
		startServiceButton.setOnClickListener(mOCL);
		stopServiceButton.setOnClickListener(mOCL);
		
		startServiceButton.setEnabled(false);
		stopServiceButton.setEnabled(false);

		startPollingServiceUpdate();
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		super.onOptionsItemSelected(item);
		switch (item.getItemId()) {
		case CLOSE_MENU:
			finish();
		}
		return false;
	}

	@Override
	public void onPause() {
		super.onPause();
		tryRemovePollingCallbacks();
		Log.i(toString(), "====>Pause");
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.i(getClass().toString(), "====>Resumed");
		if (Eula.show(this)) {
			onEulaAgreedTo();
		}
	}

	private void startPollingServiceUpdate() {
		if (status_poll_runnable == null) {
			status_poll_runnable = new Runnable() {
				public void run() {
					final boolean is_running = ServiceManager
							.findServiceInTaskList(GPSTether.this);
					Log.i(getClass().toString(),
					"Service Polling: "+ (is_running ? "=> is up and running":"=>is not running!"));

					final Button stopServiceButton =  (Button) findViewById(R.id.stopservice);
					final Button startServiceButton = (Button) findViewById(R.id.startservice);

					startServiceButton.setEnabled(!is_running);
					stopServiceButton.setEnabled(is_running);

					status_poll_handler.postDelayed(this,POLL_UPDATE_DELAY_MS);
				}
			};
		}
		// kick off a status poll for the first time!
		status_poll_handler.postDelayed(status_poll_runnable,POLL_UPDATE_DELAY_MS);
	}

	private void tryRemovePollingCallbacks() {
		if (status_poll_handler != null && status_poll_runnable != null) {
			Log.d(getClass().toString(),
				"tryRemovePollingCallbacks() => remove callbacks gets called next!");
			status_poll_handler.removeCallbacks(status_poll_runnable);
		} else {
			Log.d(getClass().toString(),
				"tryRemovePollingCallbacks() => nothing to remove!");
		}
	}

	@Override
	public void updateGPSStatus(final String status) {
		if (status != null) {
			mGPSStatus.setText(status);
		}
	}

	@Override
	public void updateServiceStatus(final String status) {
		if (status != null) {
			mServiceStatus.setText(status);
		}
	}

}
