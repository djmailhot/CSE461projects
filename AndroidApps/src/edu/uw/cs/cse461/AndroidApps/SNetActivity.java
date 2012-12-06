package edu.uw.cs.cse461.AndroidApps;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import edu.uw.cs.cse461.DB461.DB461.DB461Exception;
import edu.uw.cs.cse461.DB461.DB461.RecordSet;
import edu.uw.cs.cse461.Net.Base.NetBase;
import edu.uw.cs.cse461.Net.Base.NetLoadableAndroidApp;
import edu.uw.cs.cse461.Net.DDNS.DDNSFullName;
import edu.uw.cs.cse461.SNet.SNetController;
import edu.uw.cs.cse461.SNet.SNetDB461;
import edu.uw.cs.cse461.SNet.SNetDB461.CommunityRecord;
import edu.uw.cs.cse461.SNet.SNetDB461.PhotoRecord;
import edu.uw.cs.cse461.util.BackgroundToast;
import edu.uw.cs.cse461.util.BitmapLoader;
import edu.uw.cs.cse461.util.ContextManager;

public class SNetActivity extends NetLoadableAndroidApp implements OnItemSelectedListener {
	private static final String TAG="SNetActivity";
	public static final String PREFS_NAME = "CSE461SNETAndroid";
	private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
	private static final int CHOOSE_PICTURE_ACTIVITY_REQUEST_CODE = 200;
	
	private String pathName;
	private DDNSFullName mMyName;
	private File mGallery;
	
	private SNetController snet;
	
	private String currentSelection = "";
	
	private int currentLayout = R.layout.snet_main;
	/**
	 * The infrastructure requires a parameterless, public constructor for all NetLoadableAndroidApp's.
	 */
	public SNetActivity() {
		/**
		 * The superclass constructor requires two arguments.  The first is the application name.
		 * The second indicates whether or not the application should be listed as available by the shell program.
		 */
		super("SNet", true);
		
		pathName = Environment.getExternalStorageDirectory().getPath();
        mGallery = new File(pathName + "/" + NetBase.theNetBase().hostname());
        // Creates the gallery for the host if one does not already exist
        if ( !mGallery.exists() ) {
        	try {
        		String msg = "Gallery directory " + mGallery.getCanonicalPath() + " doesn't seem to exist.  Creating it.";
        		Log.w(TAG, msg);
    			mGallery.mkdirs();
        	} catch (IOException e) {
        		Log.e(TAG, "Critical error with gallery path name");
        		return;
        	}			
		}
        
        snet = new SNetController(pathName);
        // make sure user is in db
		mMyName = new DDNSFullName(NetBase.theNetBase().hostname());
		try {
			snet.registerBaseUsers(mMyName);
		} catch (DB461Exception e) {
			Log.e(TAG, "Failed to register myself and the root in the database");
		}
	}

	
	/** Called when the activity is first created. */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        
    }
    
    //  Allows for code reuse
    private void setLayoutMain () {
    	Log.d(TAG, "Layout is main");
    	setContentView(R.layout.snet_main);
        
        SNetDB461 database = null;
        // Attempts to set the my and chosenPhoto displays
        try {
			database = new SNetDB461(pathName + "/" + mMyName + "snet.db");
			CommunityRecord me = database.COMMUNITYTABLE.readOne(mMyName.toString());
			PhotoRecord myPhoto = database.PHOTOTABLE.readOne(me.myPhotoHash);
			PhotoRecord chosenPhoto = database.PHOTOTABLE.readOne(me.chosenPhotoHash);
			if (myPhoto == null) {
				Log.w(TAG, "Photo record for myPhoto is null");
			} else {
				((ImageView)findViewById(R.id.mypicture)).setImageBitmap(BitmapLoader.loadBitmap(myPhoto.file.getCanonicalPath(), 100, 100));
			}
			if (chosenPhoto == null) {
				Log.w(TAG, "Photo record for chosenPhoto is null");
			} else {
				((ImageView)findViewById(R.id.chosenpicture)).setImageBitmap(BitmapLoader.loadBitmap(chosenPhoto.file.getCanonicalPath(), 100, 100));
			}
			
		} catch (DB461Exception e) {
			Log.e(TAG, "Had trouble with the database");
		} catch (IOException e) { 
			Log.e(TAG, "Couldn't get the filename for the photos to work");
		} finally {
			if (database != null) {
				database.discard();
			}
		}
    }
    
    // Allows for code reuse
    private void setLayoutContact() {
    	setContentView(R.layout.snet_contact);
    	currentLayout = R.layout.snet_contact;
                
        List<CharSequence> names = new ArrayList<CharSequence>();
        try {
			SNetDB461 database = new SNetDB461(pathName + "/" + mMyName + "snet.db");
			RecordSet<CommunityRecord> records = database.COMMUNITYTABLE.readAll();
			for(CommunityRecord rec: records) {
				Log.d(TAG, "Adding value " + rec.name.toString() + " to the list");
				names.add(rec.name.toString());
			}
			// Populates the list of names with those included in the community
			database.discard();
		} catch (DB461Exception e) {
			Log.e(TAG, "Failed to access the database");
		}
		// Sets up the spinner
        Spinner spinner = (Spinner)findViewById(R.id.memberspinner);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
        Log.d(TAG, "Setting the spinner");
    }
    
    /**
     * Called after we've been unloaded from memory and are restarting.  E.g.,
     * 1st launch after power-up; relaunch after going Home.
     */
    protected void onStart() {
    	super.onStart();
        Log.d(TAG, "onStart");
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 3);
        if (settings.getInt("currentLayout", currentLayout) == R.layout.snet_main) {
        	setLayoutMain();
        	currentLayout = R.layout.snet_main;
        } else {
        	Log.d(TAG, "Layout is contact");
        	setLayoutContact();
        	currentLayout = R.layout.snet_contact;
        }
        // save my context so that this app can retrieve it later
        ContextManager.setActivityContext(this);
    }
    
    /**
     * Called whenever Android infrastructure feels like it; for example, if the user hits the Home button.
     */
    @Override
    protected void onStop() {
    	super.onStop();
    	Log.d(TAG, "onStop");
    	
    	SharedPreferences settings = getSharedPreferences(PREFS_NAME, 3);
    	SharedPreferences.Editor editor = settings.edit(); 
    	editor.putInt("currentLayout", currentLayout);
    	editor.commit();
    }
    
    /**
     * When the Choose Picture button is clicked ...
     * @param b
     */
    public void onChoosePicture(View b) {
    	Log.d(TAG, "Choose Picture selected");
    	
    	// Attempts to access the gallery
    	Intent intent = new Intent(Intent.ACTION_PICK,
    	          android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
    	intent.setType("image/*");
    	startActivityForResult(intent, CHOOSE_PICTURE_ACTIVITY_REQUEST_CODE);
    }
    
    /**
     * When the Take Picture button is clicked ...
     * @param b
     */
    public void onTakePhoto(View b) {
    	Log.d(TAG, "Take Picture selected");
    	Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    	// Do I need to specify where the gallery is to store things? YES
    	 startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
    }
    
    /**
     * Overrides onActivityResult so that I can capture and utilize the picture taken by the user
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
            	// Image captured and saved to fileUri specified in the Intent
            	Log.d(TAG, "Picture taken");
            	Bitmap photoBmp = (Bitmap)data.getExtras().get("data");
            	// Sets the image viewed to the bitmap created by the camera
            	((ImageView)findViewById(R.id.mypicture)).setImageBitmap(photoBmp);
            	                
                // Need to store the file using its hash
            	int file = photoBmp.hashCode();
            	try {
            		String filename = mGallery.getCanonicalPath() + "/" + file + ".jpg";
                	File f = new File(filename);
                	Log.d(TAG, "Trying to write the photo to file and update the database.  Photo display should already be set");
            		FileOutputStream fOut = new FileOutputStream(f);
            	    photoBmp.compress(Bitmap.CompressFormat.JPEG, 85, fOut);
            	    fOut.flush();
            	    fOut.close();
            	    // Writes the bitmap to the file
                    snet.newMyPhoto(mMyName, filename, mGallery);
                    // Then attempts to update the MyPhoto field for the user.
                    
                    // Then tries to update the gallery viewer
                    sendBroadcast(new Intent(
                        Intent.ACTION_MEDIA_MOUNTED,
                        Uri.parse("file://" + Environment.getExternalStorageDirectory())));
				} catch (DB461Exception e) {
					Log.e(TAG, "Failed to set the picture as the new myPhoto");
				} catch (IOException e) {
					Log.e(TAG, "Failed to write the picture to file");
					Log.e(TAG, "Error message was: " + e.getMessage());
				}
            } else if (resultCode == RESULT_CANCELED) {
            	// User canceled the request
            	Log.d(TAG, "User canceled taking a new picture");
            } else {
            	String msg = "Picture capturing failed";
            	Log.e(TAG, msg);
            	BackgroundToast.showToast(ContextManager.getActivityContext(), msg, Toast.LENGTH_LONG);
                // Image capture failed, advise user
            }
        } else if (requestCode == CHOOSE_PICTURE_ACTIVITY_REQUEST_CODE) { 
        	if (resultCode == RESULT_OK) {
        		// We are trying to recover the selected image from the gallery
        		// Code provided
        		Uri selectedImage = data.getData();
        		String[] filePathColumn = {MediaStore.Images.Media.DATA};

        		Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
        		cursor.moveToFirst();

        		int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        		String filePath = cursor.getString(columnIndex);
        		cursor.close();
        		try {
        			filePath = filePath.substring(mGallery.getCanonicalPath().length());
            		snet.setChosenPhoto(mMyName, mGallery.getCanonicalPath() + "/" + filePath, mGallery);
        			// Tries to set the selected photo as the user's chosen photo
        		} catch (DB461Exception e) {
        			Log.e(TAG, "We failed to set the selected photo as the new chosen photo");
        		} catch (IOException e) {
        			Log.e(TAG, "We failed to get the path name");
        		}
        	} else if (resultCode == RESULT_CANCELED) { 
        		Log.d(TAG, "User cancelled selected a new chosen photo");
        	} else {
        		Log.e(TAG, "No chosen photo was selected");
        	}
        } else {
        	// Should not have intercepted this as we utilized no other request codes
        	String msg = "We intercepted an unexpected activity";
        	Log.e(TAG, msg);
        	BackgroundToast.showToast(ContextManager.getActivityContext(), msg, Toast.LENGTH_LONG);
        }
    	// We want to return to the main menu screen no matter what
    	setContentView(R.layout.snet_main);
    }
    
    /**
     * When the Update Pictures button is clicked ...
     * @param b
     */
    public void onExchange (View b) {
    	Log.d(TAG, "Update Pictures selected");
    	setLayoutContact();
        // save my context so that this app can retrieve it later
        ContextManager.setActivityContext(this);
    }
    
    public void onBackPressed() {
    	if (currentLayout == R.layout.snet_contact) {
    		setLayoutMain();
    		currentLayout = R.layout.snet_main;
    	} else {
    		super.onBackPressed();
    	}
    }
    
    public void onItemSelected(AdapterView<?> parent, View view, 
            int pos, long id) {
        // An item was selected. Store that item
    	currentSelection = (String)parent.getItemAtPosition(pos);
    	Log.d(TAG, "User selected: " + currentSelection);
    	ContextManager.setActivityContext(this);
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }
    
    /**
     * When the Befriend button is clicked ...
     * @param b
     */
    public void onFriend (View b) {
    	Log.d(TAG, "Befriend selected");
    	try {
			snet.setFriend(new DDNSFullName(currentSelection), true);
		} catch (DB461Exception e) {
			Log.e(TAG, "Failed to set the selected name as a friend");
		}
    }
    
    /**
     * When the Contact button is clicked ...
     * @param b
     */
    public void onContact (View b) {
    	Log.d(TAG, "Contact selected");
    	try {
			snet.fetchUpdatesCaller(currentSelection, mGallery);
		} catch (DB461Exception e) {
			Log.e(TAG, "Failed to contact the community member for updates");
		}
    }
    
    /**
     * When the Unfriend button is clicked ...
     * @param b
     */
    public void onUnfriend (View b) {
    	Log.d(TAG, "Unfriend selected");
    	try {
			snet.setFriend(new DDNSFullName(currentSelection), false);
		} catch (DB461Exception e) {
			Log.e(TAG, "Failed to set the selected name as no longer a friend");
		}
    }
    
    /**
     * When the FixDB button is clicked ...
     * @param b
     */
    public void onFixDB (View b) {
    	Log.d(TAG, "FixDB selected");
    	try {
			snet.fixDB(mGallery);
			// Then tries to update the gallery viewer (otherwise the gallery won't recognize it is different)
            sendBroadcast(new Intent(
                Intent.ACTION_MEDIA_MOUNTED,
                Uri.parse("file://" + Environment.getExternalStorageDirectory())));
		} catch (DB461Exception e) {
			Log.e(TAG, "We got an error from fixDB: " + e.getMessage());
		}
    }
}
