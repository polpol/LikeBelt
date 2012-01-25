/*
 * Patrick Miller - patrick@deeplocal.com
 * Based on NFCDemo sample + info from this great post: http://mifareclassicdetectiononandroid.blogspot.com/
 */
package com.deeplocal.nfcbelt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.facebook.android.DialogError;
import com.facebook.android.Facebook;
import com.facebook.android.Facebook.DialogListener;
import com.facebook.android.FacebookError;

public class NFCBeltActivity extends Activity implements Runnable {
	private static String tagID;
	static final String TAG = "likebeltactivity";
	Facebook facebook = new Facebook("YOUR_APP_ID");
	private static NfcAdapter mAdapter;
	private static PendingIntent mPendingIntent;
	private static IntentFilter[] mFilters;
	private static String[][] mTechLists;
	LinearLayout mTagContent;
	static final int ACTIVITY_TIMEOUT_MS = 1 * 1000;
	private ProgressDialog pd;

	// helper class to handle the custom thread that needs a placeID passed to
	// it
	public class customThread implements Runnable {
		public String placeID;

		public customThread(String inplaceID) {
			placeID = inplaceID;
		}

		public void run() {
			try {
				checkIn(placeID);
				// example to post to wall if you wanted to... also pass a
				// custom msg
				// postOnWall("I just did something");
				handler.sendEmptyMessage(0);
			} catch (Exception e) {
				Log.e(TAG, "run error: " + e.getLocalizedMessage());
			}
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.main);
		View outer = findViewById(R.id.outer);
		outer.setBackgroundColor(Color.argb(255, 255, 255, 255));

		
		mAdapter = NfcAdapter.getDefaultAdapter(this);
		mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
				getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
		try {
			ndef.addDataType("*/*");
		} catch (MalformedMimeTypeException e) {
			throw new RuntimeException("fail", e);
		}
		mFilters = new IntentFilter[] { ndef, };
		mTechLists = new String[][] { new String[] { MifareClassic.class
				.getName() } };
		resolveIntent(getIntent());
	}

	// handle intent
	void resolveIntent(Intent intent) {
		String action = intent.getAction();
		if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
			Parcelable[] rawMsgs = intent
					.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			NdefMessage[] msgs;
			if (rawMsgs != null) {
				msgs = new NdefMessage[rawMsgs.length];
				for (int i = 0; i < rawMsgs.length; i++) {
					msgs[i] = (NdefMessage) rawMsgs[i];
				}
			} else {
				// a tag we cant handle
				// Unknown tag type
				byte[] empty = new byte[] {};
				NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN,
						empty, empty, empty);
				NdefMessage msg = new NdefMessage(new NdefRecord[] { record });
				msgs = new NdefMessage[] { msg };
			}
			// process the info on the tag
			processTag(msgs);
		} else {
			// auth to facebook.. only do this if key is not valid
			// if(intent.getAction().equals("android.intent.action.MAIN")){
			Log.d("FB", "FB session valid?  " + facebook.isSessionValid());
			if (!facebook.isSessionValid()) {
				facebook.authorize(this, new String[] { "publish_checkins",
						"publish_stream", "read_stream", "offline_access" },
						new DialogListener() {

							@Override
							public void onComplete(Bundle values) {
								Log.d("FB", "logged in");
							}

							@Override
							public void onFacebookError(FacebookError error) {
							}

							@Override
							public void onError(DialogError e) {
							}

							@Override
							public void onCancel() {
							}

						});
			}
			return;
		}
	}

	//process the data on the tag
		void processTag(NdefMessage[] msgs) {
			Uri serviceUri = null;
			String title = null;
			if (msgs == null || msgs.length == 0) {
				return;
			}

			List<ParsedNdefRecord> records = NdefMessageParser.parse(msgs[0]);
			final int size = records.size();
			for (int i = 0; i < size; i++) {
				ParsedNdefRecord record = records.get(i);
				if(record instanceof SmartPoster){ 
					SmartPoster tempSP = (SmartPoster)record;
					serviceUri = tempSP.getUri();
					title = tempSP.getTitle().getText();
				}else if(record instanceof UriRecord){ 
					UriRecord tempUR = (UriRecord)record;
					serviceUri = tempUR.getUri();
				}else if(record instanceof TextRecord){
					TextRecord tempT = (TextRecord)record;
					title = tempT.getText();
				}
			}

			// lets find out what we have
			if(title.startsWith("profile:")){
				Log.d(TAG, "go to this users profile");
				handleProfile(title, serviceUri);
			}else if(title.startsWith("place:")){
				Log.d(TAG, "check in to this place");
				handlePlace(title, serviceUri);
			}else if(title.startsWith("wall:")){
				Log.d(TAG, "we have a wall message");
				handleWall(title, serviceUri);
			}else if(title.startsWith("like:")){
				Log.d(TAG, "like some url");
				handleLike(title, serviceUri);
			}
		}

		//refactor these handleXYZ functions later

		public void handleLike(String title, Uri url){
			Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			v.vibrate(300);
			like(url.toString());
		}

		public void handleProfile(String title, Uri url){
			Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			v.vibrate(300);
			title=title.replace("profile:", "");
			goToProfile(title); //just throw an intent to facebook app
		}


	public void handlePlace(String title, Uri url) {
		try {
			tagID = title.replace("place:", "");
			//dialog for operation
			pd = ProgressDialog.show(this, "Checking In",
					"Checking in to Facebook", true, false);

			// Previous version had a key/val store of better images for certain places since their images were generic. Took this out for now
			// JSONObject json= getNFCJSON("http://EXTERNALURL/"+tagID);
			//pull the json for the place... add error handling later for invalid tags etc
			JSONObject data = getNFCJSON("http://graph.facebook.com/" + tagID);
			
			String imgURL = data.getString("picture");
			String placeID = data.getString("id");
			String placeName = data.getString("name");
			Runnable r = new customThread(placeID);
			new Thread(r).start();

			// vibrate for feedback
			Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			v.vibrate(300);

			//just show the location we are checking into
			TextView tv = (TextView) findViewById(R.id.title);
			tv.setVisibility(View.VISIBLE);
			tv.setText(placeName);

			ImageView ivL = (ImageView) findViewById(R.id.likebeltconfirm_image);
			ivL.setVisibility(View.VISIBLE);

			ImageView ivH = (ImageView) findViewById(R.id.likebeltlogo_image);
			ivH.setVisibility(View.INVISIBLE);

		} catch (Exception e) {
			Log.e(TAG, e.toString() + e.getMessage());
		}

	}

	public void handleWall(String title, Uri url) {
		//implement later
	}

	// some facebook methods
	public void postOnWall(String msg) {
		try {
			String response = facebook.request("me");
			Bundle parameters = new Bundle();
			parameters.putString("message", msg);
			parameters.putString("description","http://likebelt.com");
			response = facebook.request("me/feed", parameters, "POST");
			if (response == null || response.equals("")
					|| response.equals("false")) {
				Log.v("Error", "Blank response");
			}
			// hide the loading screen
			pd.dismiss();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void checkIn(String placeID) {
		try {
			String response = facebook.request("me");
			Bundle parameters = new Bundle();
			parameters.putString("place", placeID);
			parameters.putString("message", "Just checked in with LikeBelt");
			JSONObject coordinates = new JSONObject();
			// fill this in later with phone's position... for prototype just hard code it
			coordinates.put("latitude", 40.4610814);
			coordinates.put("longitude", -79.9234474);
			parameters.putString("coordinates", coordinates.toString());
			//resolve warnings with this later (bytearray instead of strings)
			response = facebook.request("me/checkins", parameters, "POST");
			if (response == null || response.equals("")
					|| response.equals("false")) {
				Log.v("Error", "Blank response");
			}
			// hide the loading screen
			pd.dismiss();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void goToProfile(String profileID) {
		String url = "fb://profile/" + profileID;
		Intent i = new Intent(Intent.ACTION_VIEW);
		i.setData(Uri.parse(url)); //just send this to FB app to friend the person
		startActivity(i);
	}

	public void like(String likeUrl) {
		//workaround since you can't like things directly from graph API
		//this opens the browser to a simple middle page with like button that user can click to like this url
		//change this to use webview later
		String url = "YOUR URL"
				+ likeUrl;
		Intent i = new Intent(Intent.ACTION_VIEW);
		i.setData(Uri.parse(url));
		startActivity(i);
	}

	public void run() {
		try {
			handler.sendEmptyMessage(0);
			// handler.sendMessage
		} catch (Exception e) {
			Log.e(TAG,e.getMessage());
		}
	}
	
	//handle json 
	//based on http://www.instropy.com/2010/06/14/reading-a-json-login-response-with-android-sdk/
	public static JSONObject getNFCJSON(String url) {
		HttpGet httpGet = new HttpGet(url);
		HttpClient httpclient = new DefaultHttpClient();
		JSONObject json = new JSONObject();
		try {
			HttpResponse response = httpclient.execute(httpGet);
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				InputStream instream = entity.getContent();
				BufferedReader reader = new BufferedReader(new InputStreamReader(instream));
				StringBuilder sb = new StringBuilder();

				String line = null;
				try {
					while ((line = reader.readLine()) != null) {
						sb.append(line + "\n");
					}
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						instream.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				json=new JSONObject(sb.toString());

				instream.close();
			}

		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return json;
	}

	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			pd.dismiss();
			Handler handler = new Handler();
			// show the check-in confirmation for 4 more seconds after completion
			handler.postDelayed(new Runnable() {
				public void run() {
					ImageView ivL = (ImageView) findViewById(R.id.likebeltconfirm_image);
					TextView tv = (TextView) findViewById(R.id.title);
					tv.setVisibility(View.INVISIBLE);
					
					ivL.setVisibility(View.INVISIBLE);
					ImageView ivH = (ImageView) findViewById(R.id.likebeltlogo_image);
					ivH.setVisibility(View.VISIBLE);
				}
			}, 4000);

		}
	};

	public void onResume() {
		super.onResume();
		mAdapter.enableForegroundDispatch(this, mPendingIntent, mFilters,
				mTechLists);
	}

	@Override
	public void onNewIntent(Intent intent) {

		resolveIntent(intent);

	}

	@Override
	public void onPause() {
		super.onPause();
		mAdapter.disableForegroundDispatch(this);
	}

	@Override
	public void setTitle(CharSequence title) {

	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		facebook.authorizeCallback(requestCode, resultCode, data);
	}
}