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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.webkit.WebView;

/**
 * Displays an EULA ("End User License Agreement") that the user has to accept
 * before using the application. Your application should call
 * {@link Eula#show(android.app.Activity)} in the onCreate() method of the first
 * activity. If the user accepts the EULA, it will never be shown again. If the
 * user refuses, {@link android.app.Activity#finish()} is invoked on your
 * activity.
 */
class Eula {
	/**
	 * callback to let the activity know when the user has accepted the EULA.
	 */
	static interface OnEulaAgreedTo {
		/**
		 * Called when the user has accepted the eula and the dialog closes.
		 */
		void onEulaAgreedTo();
	}

	private static final String ASSET_EULA = "EULA.html";
	private static final String PREFERENCE_EULA_ACCEPTED = "eula.accepted";
	private static final String PREFERENCES_EULA = "eula";

	private static void accept(final SharedPreferences preferences) {
		preferences.edit().putBoolean(Eula.PREFERENCE_EULA_ACCEPTED, true)
				.commit();
	}

	/**
	 * Closes the specified stream.
	 * 
	 * @param stream
	 *            The stream to close.
	 */
	private static void closeStream(final Closeable stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (final IOException e) {
				// Ignore
			}
		}
	}

	// private static CharSequence readEula(Activity activity) {
	private static String readEula(final Activity activity) {
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(activity.getAssets()
					.open(Eula.ASSET_EULA)));
			String line;
			final StringBuilder buffer = new StringBuilder();
			while ((line = in.readLine()) != null) {
				buffer.append(line).append('\n');
			}
			return buffer.toString();
		} catch (final IOException e) {
			return "";
		} finally {
			Eula.closeStream(in);
		}
	}

	private static void refuse(final Activity activity) {
		activity.finish();
	}

	/**
	 * Displays the EULA if necessary. This method should be called from the
	 * onCreate() method of your main Activity.
	 * 
	 * @param activity
	 *            The Activity to finish if the user rejects the EULA.
	 * @return Whether the user has agreed already.
	 */
	static boolean show(final Activity activity) {
		final SharedPreferences preferences = activity.getSharedPreferences(
				Eula.PREFERENCES_EULA, Context.MODE_PRIVATE);
		if (!preferences.getBoolean(Eula.PREFERENCE_EULA_ACCEPTED, false)) {

			final WebView wv = new WebView(activity);
			final String eula_html = Eula.readEula(activity);

			wv.loadData(eula_html, "text/html", "utf-8");
			wv.setBackgroundColor(0x00000000);
			final AlertDialog.Builder builder = new AlertDialog.Builder(
					activity);
			builder.setTitle(R.string.eula_title);
			builder.setCancelable(true);
			builder.setPositiveButton(R.string.eula_accept,
					new DialogInterface.OnClickListener() {
						public void onClick(final DialogInterface dialog,
								final int which) {
							Eula.accept(preferences);
							if (activity instanceof OnEulaAgreedTo) {
								((OnEulaAgreedTo) activity).onEulaAgreedTo();
							}
						}
					});
			builder.setNegativeButton(R.string.eula_refuse,
					new DialogInterface.OnClickListener() {
						public void onClick(final DialogInterface dialog,
								final int which) {
							Eula.refuse(activity);
						}
					});
			builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(final DialogInterface dialog) {
					Eula.refuse(activity);
				}
			});

			builder.setView(wv);
			builder.create().show();
			return false;
		}
		return true;
	}
}
