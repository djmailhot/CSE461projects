package edu.uw.cs.cse461.SNet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.DB461.DB461.DB461Exception;
import edu.uw.cs.cse461.DB461.DB461.RecordSet;
import edu.uw.cs.cse461.HTTP.HTTPProviderInterface;
import edu.uw.cs.cse461.Net.Base.NetBase;
import edu.uw.cs.cse461.Net.Base.NetLoadable.NetLoadableService;
import edu.uw.cs.cse461.Net.DDNS.DDNSException;
import edu.uw.cs.cse461.Net.DDNS.DDNSFullName;
import edu.uw.cs.cse461.Net.DDNS.DDNSFullNameInterface;
import edu.uw.cs.cse461.Net.DDNS.DDNSRRecord;
import edu.uw.cs.cse461.Net.DDNS.DDNSResolverService;
import edu.uw.cs.cse461.Net.RPC.RPCCall;
import edu.uw.cs.cse461.Net.RPC.RPCCallableMethod;
import edu.uw.cs.cse461.Net.RPC.RPCService;
import edu.uw.cs.cse461.SNet.SNetDB461.CommunityRecord;
import edu.uw.cs.cse461.SNet.SNetDB461.Photo;
import edu.uw.cs.cse461.SNet.SNetDB461.PhotoRecord;
import edu.uw.cs.cse461.util.Base64;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.Log;


/**
 * Handles UI-independent operations on SNetDB
 * 
 * @author zahorjan
 *
 */
public class SNetController extends NetLoadableService implements HTTPProviderInterface {
	private static final String TAG="SNetController";
	
	/**
	 * @return true if this is a valid generation number
	 */
	private static boolean isValidGenNum(int gen) {
		return (gen <= (int)NetBase.theNetBase().now() + 10);
	}
	
	private RPCCallableMethod fetchUpdates;
	private RPCCallableMethod fetchPhoto;
	
	/**
	 * A full path name to the sqlite database.
	 */
	private String mDBName;
	private int fetchPhotoMaxLength;
	
	private DDNSResolverService ddnsResolverService;
	
	/**
	 * IMPLEMENTED: Returns the full path name of the DB file.
	 */
	public String DBName() {
		return mDBName;
	}
	
	/**
	 * IMPLEMENTED: Ensures that the root member and the member whose name is the argument
	 * (usually the host name of this node) both have entries in the community table.
	 * @param user A user name
	 * @throws DB461Exception Some unanticipated exception occurred.
	 */
	public void registerBaseUsers(DDNSFullName user) throws DB461Exception {
		SNetDB461 db = null;
		try {
			db = new SNetDB461(mDBName);
			db.registerMember( user );
			setFriend(user, true); // Because our current implementation only stores the photos of friends
			db.registerMember( new DDNSFullName("") );  // and the root
		} catch (Exception e) {
			Log.e(TAG, "registerBaseUsers caught exception: " + e.getMessage());
			throw new DB461Exception("registerUser caught exception: " + e.getMessage());
		}
		finally {
			if ( db != null ) db.discard();
		}
	}

	/**
	 * IMPLEMENTED: Helper function that returns a string representing the community table record in the database
	 * for the user whose name is the node's hostname.
	 * @return The community table row, as a String.
	 */
	synchronized public String getStatusMsg() {
		SNetDB461 db = null;
		StringBuilder sb = new StringBuilder();
		try {
			sb.append("Host: ").append(NetBase.theNetBase().hostname()).append("\n");
			RPCService rpcService = (RPCService)NetBase.theNetBase().getService("rpc");
			sb.append("Location: ").append(rpcService.localIP()).append(":").append(rpcService.localPort()).append("\n");
			db = new SNetDB461(mDBName);
			String memberName = new DDNSFullName(NetBase.theNetBase().hostname()).toString();
			CommunityRecord member = db.COMMUNITYTABLE.readOne(memberName);
			if ( member != null ) sb.append(member.toString());
			else sb.append("No data for member '" + memberName + "'");
		} catch (Exception e) {
			Log.e(TAG, "getStatusMsg: caught exception: " + e.getMessage());
		}
		finally {
			if ( db != null ) db.discard();
		}
		return sb.toString();
	}
	
	/**
	 * Implemented: Checks db for consistency violations and tries to fix  them.
	 * Consistency requirements:
	 * <ul>
	 * <li> Every photo hash in community table should have a photo table entry
	 * <li> Ref counts should be correct
	 * <li> If a photo table entry has a file name, the file should exist, and the file's hash should correspond to the photo record key
	 * </ul>
	 * @param galleryDir A File object representing the gallery directory
	 */
	synchronized public void fixDB(File galleryDir) throws DB461Exception {
		SNetDB461 db = null;
		try {
			db = new SNetDB461(mDBName);
			Log.d(TAG, galleryDir.getCanonicalPath());
			if (!galleryDir.getAbsolutePath().equals(galleryDir.getCanonicalPath())) {
				db.checkAndFixDB(new File(galleryDir.getAbsolutePath()));
			} else {
				db.checkAndFixDB(galleryDir);
			}
		} catch (Exception e) {
			throw new DB461Exception("fixDB caught exception: "+ e.getMessage());
		}
		finally {
			if ( db != null ) db.discard();
		}
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////
	// Below here the methods may need implementation, completion, or customization.
	//////////////////////////////////////////////////////////////////////////////////////
	
	public SNetController(String dbDirName) {
		super("snet", true);

		mDBName = dbDirName + "/" + new DDNSFullName(NetBase.theNetBase().hostname()) + "snet.db";

		 try {
			ddnsResolverService = new DDNSResolverService();

			fetchUpdates = new RPCCallableMethod(this, "fetchUpdatesCallee");
			fetchPhoto = new RPCCallableMethod(this, "fetchPhotoCallee");

			RPCService rpcService = (RPCService)NetBase.theNetBase().getService("rpc");
			rpcService.registerHandler(loadablename(), "fetchUpdates", fetchUpdates );
			rpcService.registerHandler(loadablename(), "fetchPhoto", fetchPhoto );
			
			ConfigManager config = NetBase.theNetBase().config();

			this.fetchPhotoMaxLength = Integer.parseInt(config.getProperty("snet.fetchphoto_maxlength"));
			
			
		} catch (Exception e) {
			String msg = "SNet constructor caught exception: " + e.getMessage();
			Log.e(TAG, msg);
			e.printStackTrace();
		}
	}
	
	/**
	 * OPTIONAL: Provides a web interface displaying status of SNet database.
	 * @param uriVec The parsed URI argument.  (See HTTPProviderInterface.java.)
	 * @return Text to return to the browser.
	 */
	public String httpServe(String[] uriVec) throws DB461Exception {
		return "Not implemented";
	}
	
	/**
	 * Assign a photo that exists as a local file as a member's chosen photo.
	 * Decrement reference count on any existing chosen photo and delete underyling file if appropriate.
	 * @param memberName Name of member
	 * @param filename Full path name to new chosen photo file
	 * @param galleryDir File object representing the gallery directory
	 * @throws DB461Exception
	 */
	synchronized public void setChosenPhoto(DDNSFullNameInterface memberName, String filename, File galleryDir) throws DB461Exception {
		setPhoto(memberName, filename, galleryDir, "chosenPhoto");
	}

	/**
	 * This method supports the manual setting of a user's generation number.  It is probably not useful
	 * in building the SNet application per se, but can be useful in debugging tools you might write.
	 * 
	 * If no previous generation number, return -1
	 * @param memberName Name of the member whose generation number you want to set
	 * @param gen The value the generation number should be set to.
	 * @return The old value of the member's generation number
	 * @throws DB461Exception  Member doesn't exist in community db, or some unanticipated exception occurred.
	 */
	synchronized public int setGenerationNumber(DDNSFullNameInterface memberName, int gen) throws DB461Exception {
		SNetDB461 db = null;
		int old = -1;
		try {
			db = new SNetDB461(mDBName);
			CommunityRecord record = db.COMMUNITYTABLE.readOne(memberName.toString());
			// if the member does not exist
			if (record == null) {
				String msg = "Member "+memberName+ " not found when setting generation number";
				Log.e(TAG, msg);
				throw new DB461Exception(msg);
			}
			old = record.generation;
			record.generation = gen;
			db.COMMUNITYTABLE.write(record);
		} finally {
			if (db != null) {
				db.discard();
			}
		}
		return old;
	}

	/**
	 * Sets friend status of a member.  The member must already be in the DB.
	 * @param friend  Name of member whose friend status should be set.
	 * @param isfriend true to make a friend; false to unfriend.
	 * @throws DB461Exception The member is not in the community table in the DB, or some unanticipated exception occurs.
	 */
	synchronized public void setFriend(DDNSFullNameInterface friend, boolean isfriend) throws DB461Exception {
		SNetDB461 db = null;
		try {
			db = new SNetDB461(mDBName);
			CommunityRecord record = db.COMMUNITYTABLE.readOne(friend.toString());
			// if the member doesn't exist
			if (record == null) {
				String msg = "Member "+friend+ " not found when setting friend status";
				Log.e(TAG, msg);
				throw new DB461Exception(msg);
			}
			if (record.isFriend != isfriend) {
				// Makes sure we don't get incorrect data because we friended the same person multiple times without unfriending them first
				record.isFriend = isfriend;			
				db.COMMUNITYTABLE.write(record);
			
				// Need to update the photo table to reflect totals, as we currently only keep track of our friends
				// photos and want to delete a photo when no friend uses it.  Therefore, adding new friends means that
				// we now care about their photos, so if we store the file for this photo, we should ensure it sticks
				// around longer, and deleting friends means we care less about that particular photo
				if (isfriend) {
					incrementPhotoDB(db, record.myPhotoHash, null);
					incrementPhotoDB(db, record.chosenPhotoHash, null);
				} else {
					decrementPhotoDB(db, record.myPhotoHash);
					decrementPhotoDB(db, record.chosenPhotoHash);
				}
			}
			
		} catch (IOException e) { 
			Log.e(TAG, "Failed to alter the database for the member's photos");
		} finally {
			if ( db != null ) db.discard();
		}
	}
	
	/**
	 * Registers a photo as the "my" photo for a given member.
	 * Decrements the reference count of any existing my photo for that member, and deletes the underyling file for it
	 * as appropriate.
	 * @param memberName Member name
	 * @param filename  Full path name to new my photo file
	 * @param galleryDir File object describing directory in which gallery photos live
	 * @throws DB461Exception  Can't find member in community table, or some unanticipated exception occurs.
	 */
	synchronized public void newMyPhoto(DDNSFullNameInterface memberName, String filename, File galleryDir) throws DB461Exception {
		setPhoto(memberName, filename, galleryDir, "myPhoto");
	}

	/**
	 * Registers a photo as the "my" photo for a given member.
	 * Decrements the reference count of any existing my photo for that member, and deletes the underyling file for it
	 * as appropriate.
	 * @param memberName Member name
	 * @param filename  Full path name to new my photo file
	 * @param galleryDir File object describing directory in which gallery photos live
	 * @param photoType String naming the photo type.  Accepts "myPhoto" or "chosenPhoto".
	 * @throws DB461Exception  Can't find member in community table, or photo doesn't exist, some unanticipated exception occurs.
	 */
	synchronized private void setPhoto(DDNSFullNameInterface memberName, String filename, File galleryDir, String photoType) throws DB461Exception {
		SNetDB461 db = null;
		try {
			db = new SNetDB461(mDBName);
			CommunityRecord cRecord;
			int photoHash;
			int oldPhotoHash;

			// check if the member exists
			cRecord = db.COMMUNITYTABLE.readOne(memberName.toString());
			if (cRecord == null) {
				// if doesn't exist, exceptionization!
				String msg = "Member "+memberName+ " not found when setting new photo";
				Log.e(TAG, msg);
				throw new DB461Exception(msg);
			}

			// compute the photo's hash
			File photoFile = new File(filename);
			Log.i(TAG, "Call to set "+photoType+" to "+ filename);
			if (!photoFile.exists()) {
				// if doesn't exist, exceptionization!
				String msg = "Photo file "+photoFile.getCanonicalPath()+ " not found when setting new photo";
				Log.e(TAG, msg);
				throw new DB461Exception(msg);
			}

			Photo photo = new Photo(photoFile);
			photoHash = photo.hash();
			Log.v(TAG, "Computed photo hash of "+photoHash);


			incrementPhotoDB(db, photoHash, filename);


			Log.v(TAG, "Attempting to switch photo hash in member record "+cRecord.name);
			// change the community member's record
			if (photoType.equals("myPhoto")) {
				oldPhotoHash = cRecord.myPhotoHash;
				cRecord.myPhotoHash = photoHash;
			} else if (photoType.equals("chosenPhoto")) {
				oldPhotoHash = cRecord.chosenPhotoHash;
				cRecord.chosenPhotoHash = photoHash;
			} else {
				throw new IllegalArgumentException("Photo type of "+photoType+" not a valid value");
			}
			cRecord.generation = Math.max((int)NetBase.theNetBase().now(), cRecord.generation + 1);
			db.COMMUNITYTABLE.write(cRecord);


			decrementPhotoDB(db, oldPhotoHash);

			
			Log.v(TAG, "Successfully set "+photoType+" to "+photoFile.getCanonicalPath());
		} catch (IOException e) {
			String msg = "IOException when reading file of new photo";
			Log.e(TAG, msg);
			e.printStackTrace();
			throw new DB461Exception(e.getMessage());
		} finally {
			if ( db != null ) db.discard();
		}
	}

	synchronized private void incrementPhotoDB(SNetDB461 db, int photoHash, String photoFileName) throws DB461Exception {
		Log.v(TAG, "Attempting to increment record for new photo");
		// check the db for the new photo
		PhotoRecord pRecord = db.PHOTOTABLE.readOne(photoHash);
		if (pRecord == null && photoFileName != null) {
			// create a new photo in the db
			pRecord = db.PHOTOTABLE.createRecord();
			pRecord.hash = photoHash;
			pRecord.refCount = 1;
			pRecord.file = new File(photoFileName);
			db.PHOTOTABLE.write(pRecord);
			try {
				Log.v(TAG, "Created new photo record in db at: " + pRecord.file.getCanonicalPath());
			} catch (IOException e) {
				Log.e(TAG, "Sigh ... ");
			}
		} else if (pRecord != null) {
			// increment the refCount of the new photo
			pRecord.refCount++;
			Log.d(TAG, "Refcount is now: " + pRecord.refCount);
			
			db.PHOTOTABLE.write(pRecord);
			Log.d(TAG, "Modified photo record "+pRecord);
		} else {
			Log.d(TAG, "We were provided a null file name for a photo we did not already have, therefore no record was created");
		}
		
	}

	synchronized private void decrementPhotoDB(SNetDB461 db, int photoHash) throws DB461Exception, IOException {
		// deal with the old photo
		Log.v(TAG, "Attempting to decrement old photo record and delete if neccessary");
		PhotoRecord pRecord = db.PHOTOTABLE.readOne(photoHash);
		if (pRecord != null) {
			Log.d(TAG, "Old count: " + pRecord.refCount + " new count: " + (pRecord.refCount - 1));
			// decrement the refCount of the old photo
			pRecord.refCount--;
			
			if (pRecord.refCount <= 0) {
				// delete if needed
				db.PHOTOTABLE.delete(pRecord.hash);
				Log.i(TAG, "Deleted old photo record for "+pRecord.file);
				boolean deleted = pRecord.file.delete();
				if (!deleted) {
					Log.w(TAG, "photo file "+pRecord.file.getCanonicalPath()+" failed to be deleted when refCount reached 0");
				}
				pRecord = null;
			} else {
				// else save the change
				db.PHOTOTABLE.write(pRecord);
			}
		}
	}
	
	/**
	 * The caller side of fetchUpdates.
	 * @param friend The friend to be contacted
	 * @param galleryDir The path name to the gallery directory
	 * @throws DB461Exception Something went wrong...
	 */
	synchronized public void fetchUpdatesCaller( String friend, File galleryDir) throws DB461Exception {
		SNetDB461 db = null;
		try {
			db = new SNetDB461(mDBName);
			DDNSRRecord.ARecord ddnsResult;
			JSONObject args;

			Log.i(TAG, "Call to fetchUpdatesCaller for friend "+friend);

			// Resolve the friend to a ddns record
			ddnsResult = ddnsResolverService.resolve(friend);
			Log.v(TAG, "Resolved friend "+friend+" to DDNS address "+ddnsResult);

			Log.d(TAG, "Putting together JSON glob");
			// Community info glob
			JSONObject community = new JSONObject();
			// Needphotos info glob
			JSONArray needphotos = new JSONArray();

			Map<Integer, Integer> photoRefCounts = new HashMap<Integer, Integer>();
			RecordSet<CommunityRecord> communityRecords = db.COMMUNITYTABLE.readAll();
			for (CommunityRecord cRecord : communityRecords) {
				JSONObject value = new JSONObject()
						.put("generation", cRecord.generation)
						.put("myphotohash", cRecord.myPhotoHash)
						.put("chosenphotohash", cRecord.chosenPhotoHash);

				community.put(cRecord.name.toString(), value);

				// if the memeber is a friend, make sure we have their most recent photo data
				if (cRecord.isFriend) {
					PhotoRecord pRecord;
					int photoHash;
					// check the 'myPhoto', noting that a hash == 0 means no photo
					pRecord = db.PHOTOTABLE.readOne(cRecord.myPhotoHash);
					photoHash = cRecord.myPhotoHash;
					if (pRecord == null && photoHash != 0) {
						needphotos.put(photoHash);

						Integer refCount = photoRefCounts.get(photoHash);
						photoRefCounts.put(photoHash, (refCount == null ? 1 : refCount + 1) );
					}
					// check the 'chosenPhoto', noting that a hash == 0 means no photo
					pRecord = db.PHOTOTABLE.readOne(cRecord.chosenPhotoHash);
					photoHash = cRecord.chosenPhotoHash;
					if (pRecord == null && photoHash != 0) {
						needphotos.put(photoHash);

						Integer refCount = photoRefCounts.get(photoHash);
						photoRefCounts.put(photoHash, (refCount == null ? 1 : refCount + 1) );
					}
				}
			}

			// fetch the updates
			args = new JSONObject()
				.put("community", community)
				.put("needphotos", needphotos);

			// SEND IT OFF INTO THE GREAT INTERNET
			Log.d(TAG, "sending fetchUpdates RPC call with args: "+args);
			JSONObject response = RPCCall.invoke(ddnsResult.ip(), ddnsResult.port(), "snet", "fetchUpdates", args);

			Log.d(TAG, "response JSON of "+response);

			Iterator<String> keysIter;

			// iterate over response
			JSONObject communityUpdates = response.getJSONObject("communityupdates");
			keysIter = communityUpdates.keys();
			while (keysIter.hasNext()) {
				String name = keysIter.next();
				CommunityRecord cRecord = db.COMMUNITYTABLE.readOne(name);
				if (cRecord == null) {
					Log.i(TAG, "creating new db record for "+name);
					cRecord = db.COMMUNITYTABLE.createRecord();
					cRecord.name = new DDNSFullName(name);
					cRecord.isFriend = false;
				}
				JSONObject recordUpdate = communityUpdates.getJSONObject(name);		
				// check if the updated record is really more recent than ours (but not from THE FUTURE)
				int genNum = recordUpdate.getInt("generation");
				if (cRecord.generation <= genNum && isValidGenNum(genNum)) {
					Log.v(TAG, "old record for "+name+" was "+cRecord);
					Log.d(TAG, "updating record for "+name+" to "+recordUpdate);
					cRecord.generation = genNum;

					int oldHash = cRecord.myPhotoHash;
					int newHash = recordUpdate.getInt("myphotohash");
					if (oldHash != newHash) {
						
						Integer refCount = photoRefCounts.get(oldHash);
						photoRefCounts.put(oldHash, (refCount == null ? -1 : refCount - 1) );

						refCount = photoRefCounts.get(newHash);
						photoRefCounts.put(newHash, (refCount == null ? 1 : refCount + 1) );

						cRecord.myPhotoHash = newHash;
					}
					oldHash = cRecord.chosenPhotoHash;
					newHash = recordUpdate.getInt("chosenphotohash");
					if (oldHash != newHash) {

						Integer refCount = photoRefCounts.get(oldHash);
						photoRefCounts.put(oldHash, (refCount == null ? -1 : refCount - 1) );

						refCount = photoRefCounts.get(newHash);
						photoRefCounts.put(newHash, (refCount == null ? 1 : refCount + 1) );

						cRecord.chosenPhotoHash = newHash;
					}
				} else {
					// something went wrong
					Log.i(TAG, "fetchUpdates returned record for "+name+", but was not more recent than ours");
				}
				db.COMMUNITYTABLE.write(cRecord);
			}


			JSONArray photoHashes = response.getJSONArray("photoupdates");
			Set<Integer> photoHashesSet = new HashSet<Integer>();
			for (int i=0; i<photoHashes.length(); i++) {
				photoHashesSet.add(photoHashes.getInt(i));
			}


			for (Integer hash : photoRefCounts.keySet()) {
				Integer refCount = photoRefCounts.get(hash);
				
				PhotoRecord pRecord = db.PHOTOTABLE.readOne(hash);
				if (pRecord != null) {
					pRecord.refCount += refCount;


				// if not in DB and we have a photoUpdate for it (and the refs to it are positive)
				} else if (pRecord == null && photoHashesSet.contains(hash) && refCount > 0) {
					// create a new photo in the db
					pRecord = db.PHOTOTABLE.createRecord();
					pRecord.hash = hash;
					pRecord.refCount = refCount;
					pRecord.file = new File(galleryDir.getCanonicalPath() + File.separatorChar + hash + ".jpg");
					Log.v(TAG, "Created new photo record in db");

					// finally, fetch photo data for the photo
					fetchPhotoAndSave(ddnsResult.ip(), ddnsResult.port(), pRecord);
				}
				// else photoUpdate doesn't have photo data


				// if we have a pRecord, write it or delete it
				if (pRecord != null) {
					if (pRecord.refCount > 0) {
						// write it
						db.PHOTOTABLE.write(pRecord);
					} else {
						// delete it
						db.PHOTOTABLE.delete(pRecord.hash);
						Log.i(TAG, "Deleted old photo record for "+pRecord.file);
						boolean deleted = pRecord.file.delete();
						if (!deleted) {
							Log.w(TAG, "photo file "+pRecord.file.getCanonicalPath()+" failed to be deleted when refCount reached 0");
						}
					}
				}
			}
			
		} catch(DDNSException e) {
			e.printStackTrace();
		} catch(JSONException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if ( db != null ) db.discard();
		}
	}
	
	// Traverses the array provided to see if the provided value is present.  Must be linear search because the
	// values are not ordered
	private boolean contains(JSONArray array, int value) throws JSONException {
		for (int i = 0; i < array.length(); i++) {
			if (array.getInt(i) == value) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * The callee side of fetchUpdates - invoked via RPC.
	 * @param args JSONObject containing commmunity JSONObject and needphotos JSONArray (described in assignment)
	 * @return JSONObject containing communityupdates JSONObject and photoupdates JSONArray (described in assignment)
	 */
	synchronized public JSONObject fetchUpdatesCallee(JSONObject args) throws Exception {
		JSONObject community = args.getJSONObject("community"); // Community they know about
		JSONArray needphotos = args.getJSONArray("needphotos"); // Photos they want
		
		JSONArray photoupdates = new JSONArray(); // Photos that I store that they either wanted or may want because the community has changed
		JSONObject communityupdates = new JSONObject(); // Community members for which I have a better generation number.
		
		Map<Integer, Integer> photoRefCounts = new HashMap<Integer, Integer>();
		
		SNetDB461 database = new SNetDB461(mDBName); // The database of info I have
		Iterator<String> keys = community.keys();
		Log.v(TAG, "assumed that the iterator contains strings");
		while (keys.hasNext()) {
			// Traverses their community values to see where it differs from mine.  Stores all the information of mine that is more up to date than
			// theirs.
			String name = keys.next();
			CommunityRecord myVals = database.COMMUNITYTABLE.readOne(name);
			JSONObject theirVals = community.getJSONObject(name);		
			int genNum = theirVals.getInt("generation");
			// if I don't have a record, or their record is better (but not from THE FUTURE), then update mine
			if (myVals == null || (myVals.generation < genNum && isValidGenNum(genNum))) {
				Log.d(TAG, "We don't currently have a record for: " + name);
				if (myVals == null) {
					// I don't have this member of the community, so I should store the data for later
					myVals = database.COMMUNITYTABLE.createRecord();
					myVals.name = new DDNSFullName(name);
					myVals.isFriend = false;
				}
				myVals.generation = genNum; 
				
				int oldHash = myVals.myPhotoHash;
				int newHash = theirVals.getInt("myphotohash");
				if (oldHash != newHash) {
					
					Integer refCount = photoRefCounts.get(oldHash);
					photoRefCounts.put(oldHash, (refCount == null ? -1 : refCount - 1) );

					refCount = photoRefCounts.get(newHash);
					photoRefCounts.put(newHash, (refCount == null ? 1 : refCount + 1) );

					myVals.myPhotoHash = newHash;
				}
				oldHash = myVals.chosenPhotoHash;
				newHash = theirVals.getInt("chosenphotohash");
				if (oldHash != newHash) {

					Integer refCount = photoRefCounts.get(oldHash);
					photoRefCounts.put(oldHash, (refCount == null ? -1 : refCount - 1) );

					refCount = photoRefCounts.get(newHash);
					photoRefCounts.put(newHash, (refCount == null ? 1 : refCount + 1) );

					myVals.chosenPhotoHash = newHash;
				}
				
				
				database.COMMUNITYTABLE.write(myVals);
			} else if (myVals != null && myVals.generation > genNum) {
				// Their data is not up to date, so we need to put our information into the updates to send
				Log.d(TAG, "Our record is more up to date than theirs for: " + name);
				JSONObject update = new JSONObject();
				update.put("generation", myVals.generation);
				update.put("myphotohash", myVals.myPhotoHash);
				if (database.PHOTOTABLE.readOne(myVals.myPhotoHash) != null) {
					if (!contains(photoupdates, myVals.myPhotoHash)) { 
						// check if we need to add the hash to the list of photohashes.  
						// This will only happen if it is not already present and we actually store the data we have told them about.
						photoupdates.put(myVals.myPhotoHash);
					}
				}
				update.put("chosenphotohash", myVals.chosenPhotoHash);
				if (database.PHOTOTABLE.readOne(myVals.chosenPhotoHash) != null) {
					if (!contains(photoupdates, myVals.chosenPhotoHash)) {
						// check if we need to add the hash to the list of photohashes
						// This will only happen if it is not already present and we actually store the data we have told them about.
						photoupdates.put(myVals.chosenPhotoHash);
					}
				}
				communityupdates.put(name, update);
			} // If our generation numbers are the same, do nothing
		}
		
		for (Integer hash : photoRefCounts.keySet()) {
			Integer refCount = photoRefCounts.get(hash);
			
			PhotoRecord pRecord = database.PHOTOTABLE.readOne(hash);
			if (pRecord != null) {
				pRecord.refCount += refCount;

			} 
			// else photoUpdate doesn't have photo dataS

			// if we have a pRecord, write it or delete it
			if (pRecord != null) {
				if (pRecord.refCount > 0) {
					// write it
					database.PHOTOTABLE.write(pRecord);
				} else {
					// delete it
					database.PHOTOTABLE.delete(pRecord.hash);
					Log.i(TAG, "Deleted old photo record for "+pRecord.file);
					boolean deleted = pRecord.file.delete();
					if (!deleted) {
						Log.w(TAG, "photo file "+pRecord.file.getCanonicalPath()+" failed to be deleted when refCount reached 0");
					}
				}
			}
		}
		
		// Traverses all of our community records to see if we know about any member they do not.
		RecordSet<CommunityRecord> myCom = database.COMMUNITYTABLE.readAll();
		for (CommunityRecord rec : myCom) {
			String name = rec.name.toString();
			if (!community.has(name)) {
				Log.d(TAG, "We have a record they do not for: " + name);
				// The name is not in their list of community members, so we must add it to the updates we will send back
				JSONObject update = new JSONObject();
				update.put("generation", rec.generation);
				update.put("myphotohash", rec.myPhotoHash);
				update.put("chosenphotohash", rec.chosenPhotoHash);
				if (database.PHOTOTABLE.readOne(rec.myPhotoHash) != null) {
					if (!contains(photoupdates, rec.myPhotoHash)) { 
						// check if we need to add the hash to the list of photohashes.  
						// This will only happen if it is not already present and we actually store the data we have told them about.
						photoupdates.put(rec.myPhotoHash);
					}
				}
				if (database.PHOTOTABLE.readOne(rec.chosenPhotoHash) != null) {
					if (!contains(photoupdates, rec.chosenPhotoHash)) {
						// check if we need to add the hash to the list of photohashes
						// This will only happen if it is not already present and we actually store the data we have told them about.
						photoupdates.put(rec.chosenPhotoHash);
					}
				}
				communityupdates.put(name, update);
			}
		}
		
		for (int i = 0; i < needphotos.length(); i++) {
			// Traverse the photos they say they need
			int currenthash = needphotos.getInt(i);
			if (!contains(photoupdates, currenthash)) {
				// If we haven't already put that into our updates, check if we have it.
				if (database.PHOTOTABLE.readOne(currenthash) != null) {
					// If we have it, tell them we do.
					Log.d(TAG, "We have a photo they requested of hash: " + currenthash);
					photoupdates.put(currenthash);
				}
			}
		}
		Log.d(TAG, "We are returning our results");
		JSONObject result = new JSONObject();
		result.put("photoupdates", photoupdates);
		result.put("communityupdates", communityupdates);
		database.discard();
		return result;
	}	
	
	/**
	 * Caller side of fetchPhoto (fetch one photo).
	 * @throws Exception
	 */
	synchronized private void fetchPhotoAndSave(String ip, int port, PhotoRecord pRecord) {
		FileOutputStream outputStream = null;
		try {
			// open a stream to the file
			outputStream = new FileOutputStream(pRecord.file);

			Log.v(TAG, "Fetching photo "+pRecord.file.getCanonicalPath());

			JSONObject args = new JSONObject()
					.put("photohash", pRecord.hash)
					.put("maxlength", fetchPhotoMaxLength);

			int requestOffset = 0;
			// break when the response indicates the file is finished
			while (true) {
				// update the arguments offset
				args.put("offset", requestOffset);

				Log.v(TAG, "Sending fetchPhoto RPC call with args "+args);

				// send off the RPC call
				JSONObject response = RPCCall.invoke(ip, port, "snet", "fetchPhoto", args);
				Log.d(TAG, "DAIN IS A FOOOOOOO and bakes amazing things");
				int responsePhotoHash = response.getInt("photohash");
				int responseOffset = response.getInt("offset");
				int responseLength = response.getInt("length");
				// The file should be finished transferring
				if (responseLength == 0) {
					break;
				}
				
				String encodedData = response.getString("photodata");
				byte[] decodedData = Base64.decode(encodedData);

				Log.v(TAG, "fetchPhoto RPC call response of "+args);

				

				// check for crazies
				if (pRecord.hash != responsePhotoHash || requestOffset != responseOffset) {
					// SOMETHING WENT REALLY WRONG
				}

				outputStream.write(decodedData, 0, responseLength);
				
				// stream write responseLength number of bytes to the file
				requestOffset += responseLength;

			}

			Log.v(TAG, "fetchPhoto save complete");

		} catch (FileNotFoundException e) {
			Log.w(TAG, "Photo file not found when fetching photo");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (outputStream != null) {
				try {
					outputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	/**
	 * Callee side of fetchPhoto (fetch one photo).  To fetch an image file, call this
	 * method repeatedly, starting at offset 0 and incrementing by the returned length each
	 * subsequent call.  Repeat until a length of 0 comes back.
	 * @param args {photoHash: int, maxlength: int, offset: int}
	 * @return {photoHash: int, photoData: String (base64 encoded byte[]), length: int, offset: int}.  The photoData field may
	 *     not exist if the length is 0.
	 * @throws Exception
	 */
	synchronized public JSONObject fetchPhotoCallee(JSONObject args) throws Exception {
		int hash = args.getInt("photohash");
		int maxlength = args.getInt("maxlength");
		int offset = args.getInt("offset");	
		// get all the actual args

		Log.v(TAG, "fetchPhoto RPC request with args "+args);
		
		SNetDB461 database = new SNetDB461(mDBName); // The database of info I have
		PhotoRecord rec = database.PHOTOTABLE.readOne(hash);
		JSONObject result = new JSONObject();
		result.put("photohash", hash);
		result.put("offset", offset);
		// We want this information in our result to return whether we have the information or not.
		if (rec != null) {
			Log.i(TAG, "We have a PhotoRecord for this hash");
			File file = rec.file;
			if (file == null) {
				Log.e(TAG, "We have a PhotoRecord with a non-existant file! AAAAAH");
				throw new Exception("I'm sorry, I can't do that, Dave");
			}
			
			if (offset > file.length()) {
				// If the offset asked for is beyond the length of the file, we cannot return anything, so set the length as 0
				result.put("length", 0);
			} else {
				byte[] unencoded = new byte[maxlength];
				FileInputStream stream = new FileInputStream(file);
				Log.i(TAG, "We are reading the file " + file.getName());
				// Gets the file
				long skipped = 0;
				while (skipped < offset - 1) {
					skipped += stream.skip((offset -1) - skipped);
				} // Needs to continue skipping until we have reached the offset.
				int length = stream.read(unencoded);
				// Reads the appropriate file bytes into the array
				result.put("length", length);
				String photoData = Base64.encodeBytes(unencoded, 0, length);
				result.put("photodata", photoData);
				Log.d(TAG, "We have encoded the data");
			}
			
		} else {
			result.put("length", 0);
		}
		Log.v(TAG, "fetchPhoto RPC request returning result of "+result);
		return result;
	}

	@Override
	public String dumpState() {
		return null;
	}
	
}
	

