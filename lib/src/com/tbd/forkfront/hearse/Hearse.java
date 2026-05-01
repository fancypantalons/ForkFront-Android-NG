package com.tbd.forkfront.hearse;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;
import com.tbd.forkfront.Log;
import com.tbd.forkfront.R;
import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class communicates with the Hearse server and provides all Hearse functionality.
 * @author Ranbato
 */
public class Hearse {

	private final String CLIENT_ID;
	private final String HEARSE_CRC;
	static final String HOST = "hearse.krollmark.com";
	// TODO: hearse.krollmark.com does not appear to support HTTPS; verify before switching
	static String BASE_URL = "http://hearse.krollmark.com/bones.dll?act=";	// hearse commands
	private static final String NEW_USER = "newuser";
	private static final String UPLOAD = "upload";
	private static final String DOWNLOAD = "download";
	private static final String BONES_CHECK = "bonescheck";
	private static final String UPDATE_USER = "changeuserinfo";
	private static final String HEADER_TOKEN = "X_USERTOKEN";
	private static final String HEADER_EMAIL = "X_USEREMAIL";
	private static final String HEADER_NICK = "X_USERNICK";
	private static final String HEADER_HEARSE_CRC = "X_HEARSECRC";
	//    private static final String HEADER_VERSION = "X_VER";
//    # 1 = incarnation = major, minor, patchlevel, editlevel
//    # 2 = feature set
//    # 3 = entity count
//    # 4 = struct sizes = flag, obj, monst, you
//    private static final int HEADER_VERSION_COUNT = 4;
	private static final String HEADER_BONES_CRC = "X_BONESCRC";
	private static final String HEADER_VERSIONCRC = "X_VERSIONCRC";
	private static final String HEADER_FILE_NAME = "X_FILENAME";
	private static final String HEADER_USER_LEVELS = "X_USERLEVELS";
	private static final String HEADER_NETHACKVER = "X_NETHACKVER";
	private static final String HEADER_FORCEDOWNLOAD = "X_FORCEDOWNLOAD";
	/**
	 * Not implemented
	 */
	private static final String HEADER_FORCE_UPDATE = "X_FORCEUPDATE";
	/**
	 * Not implemented.
	 */
	private static final String HEADER_MATCHBONES = "X_MATCHBONES";
	private static final String HEADER_WANTS_INFO = "X_GIVEINFO";
	private static final String HEADER_MOTD = "X_MOTD";
	private static final String HEADER_CLIENT = "X_CLIENTID";
	private static final String HEADER_HEARSE = "X-HEARSE";
	private static final String HEADER_ERROR = "X_ERROR";
	private static final String F_ERROR_FATAL = "FATAL";
	private static final String F_ERROR_INFO = "INFO";
	/**
	 * bon<dungeon code><0 | role code>.<level boneid | level number>
	 * XXX case_tolerant if appropriate
	 */
	private final Pattern PATTERN;
	private static final String TAG = "MD5";
	private static final String PREFS_HEARSE_ID = "hearseID";
	private static final String PREFS_HEARSE_MAIL = "hearseMail";
	private static final String PREFS_HEARSE_NAME = "hearseName";
	private static final String PREFS_HEARSE_KEEP_UPLOADED = "hearseKeepUploaded";
	private static final String PREFS_HEARSE_ENABLE = "hearseEnable";
	private static final String PREFS_HEARSE_UPDATE_USER = "hearseUpdateUser";
	private static final String PREFS_HEARSE_LAST_UPLOAD = "hearseLastUpload";
	private final Context context;  // Application context to avoid leaks
	private final Handler mainHandler;  // For posting UI operations to main thread
	private final SharedPreferences prefs;
	private final String dataDirString;
	private String userNick;
	private String userEmail;
	private String userToken;
	private boolean keepUploaded;
	private long lastUpload;
	private boolean mLittleEndian;
	private String mNethackVersion;

	/**
	 * Creates a new instance of Hearse
	 *
	 * @param context Application context (use getApplicationContext() to avoid Activity leaks)
	 * @param prefs SharedPreferences
	 * @param path nethack datadir
	 */
	public Hearse(Context context, SharedPreferences prefs, String path) {

		// Store Application context to avoid Activity leaks
		this.context = context.getApplicationContext();
		// Handler for posting UI operations to main thread
		this.mainHandler = new Handler(Looper.getMainLooper());
		dataDirString = path;
		this.prefs = prefs;

		CLIENT_ID = context.getResources().getString(R.string.hearseClientName);
		HEARSE_CRC = getStringMD5(CLIENT_ID);
		PATTERN = Pattern.compile("^bon[A-Z](0|(" + context.getResources().getString(R.string.hearseRoles) + "))\\.([A-Z]|\\d+)\\z", Pattern.CASE_INSENSITIVE);
		if(HEARSE_CRC == null)
			return;
		mLittleEndian = context.getResources().getBoolean(R.bool.hearseLittleEndian);
		mNethackVersion = context.getResources().getString(R.string.hearseNethackVersion);

		userToken = prefs.getString(PREFS_HEARSE_ID, "");
		userEmail = prefs.getString(PREFS_HEARSE_MAIL, "");
		userNick = prefs.getString(PREFS_HEARSE_NAME, "");
		keepUploaded = prefs.getBoolean(PREFS_HEARSE_KEEP_UPLOADED, false);
		lastUpload = prefs.getLong(PREFS_HEARSE_LAST_UPLOAD, 0);

		prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
	}

		public void start() {
		if(prefs.getBoolean(PREFS_HEARSE_ENABLE, false)) {
		        if(hearseThread.getState() == Thread.State.NEW) {
		                hearseThread.start();
		        }
		}
		}

	public void destroy() {
		prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
	}
	static boolean checkMD5(String md5, File updateFile) {
		if (md5 == null || md5.length() == 0 || updateFile == null) {
			return false;
		}

		String calculatedDigest = getFileMD5(updateFile);
		if (calculatedDigest == null) {
			return false;
		}
		return calculatedDigest.equalsIgnoreCase(md5);
	}

	static String getByteMD5(byte[] bytesOfMessage) {

	        MessageDigest md = null;
	        try {
	                md = MessageDigest.getInstance("MD5");
	        } catch (NoSuchAlgorithmException e) {
	                Log.print(e.toString());
	                return null;
	        }
	        byte[] md5sum = md.digest(bytesOfMessage);
	        BigInteger bigInt = new BigInteger(1, md5sum);
	        String output = bigInt.toString(16);
	        // Fill to 32 chars
	        output = String.format("%32s", output).replace(' ', '0');
	        return output;
	}

	static String getStringMD5(String input) {
	        byte[] bytesOfMessage;
	        try {
	                bytesOfMessage = input.getBytes("UTF-8");
	        } catch (UnsupportedEncodingException e) {
	                Log.print(e.toString());
	                return null;
	        }

	        return getByteMD5(bytesOfMessage);
	}

	static String getFileMD5(File updateFile) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			return null;
		}

		try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(updateFile))) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = is.read(buffer)) > 0) {
				digest.update(buffer, 0, read);
			}
			byte[] md5sum = digest.digest();
			BigInteger bigInt = new BigInteger(1, md5sum);
			String output = bigInt.toString(16);
			// Fill to 32 chars
			output = String.format("%32s", output).replace(' ', '0');
			return output;
		} catch (IOException e) {
			throw new RuntimeException("Unable to process file for MD5", e);
		}
	}

	/**
	 * Collects all of the files that match bones file name pattern.
	 *
	 * @return array of all bones files
	 */
	private File[] enumerateBones() {

		File dataDir = new File(dataDirString);
		File[] bones = dataDir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				return isValidBonesFileName(filename);
			}
		});
		return bones == null ? new File[0] : bones;
	}

	/**
	 * The main method.  Registers new user and uploads and downloads bones files.
	 * Originally called drive() when single threaded. :)
	 *
	 */
	private Thread hearseThread = new Thread() {
		@Override
		public void run() {

			try {
				if(userToken.length() == 0) {
					if(userEmail.length() > 0) {
						userToken = createNewUser();
					} else {
						showEmailRequired();
					}
				} else {
					Log.print("using existing token " + userToken);

					// Check if userNick information has changed and update.
					if(prefs.getBoolean(PREFS_HEARSE_UPDATE_USER, false)) {
						if(userEmail.length() > 0) {
							changeUserInfo();
						} else {
							showEmailRequired();
						}
					}
				}

				if(prefs.contains(PREFS_HEARSE_UPDATE_USER))
					prefs.edit().remove(PREFS_HEARSE_UPDATE_USER).apply();

				if(userToken.length() > 0) {
					int nUp = uploadBones();
					int nDown = 0;
					if(nUp > 0) {
						nDown = downloadBones();
					}
					Log.print("Hearse uploaded " + nUp + ", downloaded " + nDown);

					if(nUp > 0 || nDown > 0) {
						updateLastUpload();
					}
				}
			} catch(Exception e) {
				showToast("Hearse not reachable");
				e.printStackTrace();
			}
		}
	};

	private void updateLastUpload() {

		for(File bones : enumerateBones()) {
			long lastModified = bones.lastModified();
			if(lastModified > lastUpload)
				lastUpload = lastModified;
		}
		prefs.edit().putLong(PREFS_HEARSE_LAST_UPLOAD, lastUpload).apply();
	}

	private void showEmailRequired() {
		showToast("Hearse requires an Email address to register");
	}

	private void showToast(final String message) {
		// Post to main thread using Handler (Application context is safe for Toast)
		mainHandler.post(new Runnable() {
			@Override
			public void run() {
				Log.print(message);
				Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
			}
		});
	}

	String createNewUser() throws IOException {
		List<HearseHeader> headerList = new ArrayList<HearseHeader>();


		headerList.add(new HearseHeader(HEADER_TOKEN, userEmail));

		headerList.add(new HearseHeader(HEADER_NICK, userNick));

		HearseResponse resp = doGet(BASE_URL, NEW_USER, headerList);

		if (resp.getFirstHeader(HEADER_HEARSE) == null) {
			return "";
		}
		if (resp.getFirstHeader(HEADER_ERROR) != null) {
			printContent(resp);
			return "";

		}

		String tokenValue = resp.getFirstHeader(HEADER_TOKEN);
		SharedPreferences.Editor ed = prefs.edit();
		ed.putString(PREFS_HEARSE_ID, tokenValue);
		ed.apply();
		return tokenValue;
	}

	int downloadBones() throws IOException {
		int nDownloaded = 0;
		String hackver = prefs.getString(HEADER_NETHACKVER, mNethackVersion);

		StringBuilder builder = new StringBuilder();
		for(File bones : enumerateBones()) {
			builder.append(bones.getName());
			builder.append(',');
		}
		String existingBonesSet = builder.toString();

		while (true) {

			List<HearseHeader> headerList = new ArrayList<HearseHeader>();

			headerList.add(new HearseHeader(HEADER_TOKEN, userToken));
			if(existingBonesSet.length() > 0)
				headerList.add(new HearseHeader(HEADER_USER_LEVELS, existingBonesSet));
			//isEmpty requires API 9
			if (!"".equals(hackver)) {
				headerList.add(new HearseHeader(HEADER_NETHACKVER, hackver));
			}

			HearseResponse resp = doGet(BASE_URL, DOWNLOAD, headerList);

			if (resp.getFirstHeader(HEADER_HEARSE) == null) {
				return 0;
			}
			String errorHeader = resp.getFirstHeader(HEADER_ERROR);
			if (errorHeader != null) {

				if (errorHeader.equals(F_ERROR_INFO)) {
					// This is a warning so pretend we succeeded.
					printContent(resp);
				} else {
					printContent(resp);
				}
				break;
			} else {

				String fileNameValue = resp.getFirstHeader(HEADER_FILE_NAME);
				String md5Value = resp.getFirstHeader(HEADER_BONES_CRC);

				File bonesFile = new File(dataDirString, fileNameValue);
				// For thread safety, don't download as real name.  Nethack might try to load it before complete
				File tmpBonesFile = new File(dataDirString, bonesFile.getName() + ".tmp");

				try (InputStream in = resp.getInputStream();
				     BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tmpBonesFile))) {
					tmpBonesFile.createNewFile();
					byte[] buffer = new byte[8192];
					int read;
					while ((read = in.read(buffer)) != -1) {
						out.write(buffer, 0, read);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				if(checkMD5(md5Value, tmpBonesFile)) {
					if(tmpBonesFile.renameTo(bonesFile)) {
						Log.print("Downloaded " + bonesFile.getName());
						existingBonesSet = existingBonesSet + bonesFile.getName() + ",";
						nDownloaded++;
					} else {
						Log.print("Failed to rename bones file from " + tmpBonesFile.getName() + " to " + bonesFile.getName());
						tmpBonesFile.delete();
					}
				} else {
					//arg
					Log.print("Bad bones downloaded");
					tmpBonesFile.delete();
				}

			}
		}

		return nDownloaded;
	}

	private int uploadBones() {
		List<File> newBones = new ArrayList<File>(5);

		// Add all bones files that have been modified since the last upload
		for (File bonesFile : enumerateBones()) {
			long lastModified = bonesFile.lastModified();
			if(lastModified > lastUpload) {
				newBones.add(bonesFile);
			}
		}

		return uploadBonesFiles(newBones);
	}

	int uploadBonesFiles(List<File> files) {
		int nUploaded = 0;
		String currentFileName;

		SharedPreferences.Editor ed = prefs.edit();

		for (int i = 0; i < files.size(); i++) {

			currentFileName = files.get(i).getName();

			List<HearseHeader> headerList = new ArrayList<HearseHeader>();

			headerList.add(new HearseHeader(HEADER_TOKEN, userToken));
			headerList.add(new HearseHeader(HEADER_FILE_NAME, currentFileName));
			if (i == 0) {
				headerList.add(new HearseHeader(HEADER_WANTS_INFO, "Y"));
			}

			NHFileInfo info = loadFile(files.get(i));

			String info1 = info.get1();
			String info2 = info.get2();
			String info3 = info.get3();
			String info4 = info.get4();
//            headerList.add(new HearseHeader(HEADER_VERSION + 1, info.get1()));
//            headerList.add(new HearseHeader(HEADER_VERSION + 2, info.get2()));
//            headerList.add(new HearseHeader(HEADER_VERSION + 3, info.get3()));
//            headerList.add(new HearseHeader(HEADER_VERSION + 4, info.get4()));
			headerList.add(new HearseHeader(HEADER_VERSIONCRC, getStringMD5(info1 + "," + info2 + "," + info3 + "," + info4)));

			headerList.add(new HearseHeader(HEADER_BONES_CRC, info.md5));

			HearseResponse resp;
			try {
				resp = doPost(BASE_URL, UPLOAD, headerList, info.data);
			} catch (IOException e) {
				// Log exception
				e.printStackTrace();
				continue;
			}

			if (resp.getFirstHeader(HEADER_HEARSE) == null) {
				return 0;
			}

			boolean uploaded = false;
			String errorValue = resp.getFirstHeader(HEADER_ERROR);
			if (errorValue != null) {

				if (errorValue.equals(F_ERROR_INFO)) {
					// This is a warning so pretend we succeeded.
					uploaded = true;
					nUploaded++;
					printContent(resp);
				} else {
					printContent(resp);
				}
			} else {
				// Save the version for requests. Will help prevent bad bones.
				String hackVerValue = resp.getFirstHeader(HEADER_NETHACKVER);
				if (hackVerValue != null) {
					ed.putString(HEADER_NETHACKVER, hackVerValue);
				}
				Log.print("Uploaded " + currentFileName);
				uploaded = true;
				nUploaded++;

				String motdValue = resp.getFirstHeader(HEADER_MOTD);
				if (motdValue != null) {
					Log.print(HEADER_MOTD + ":" + motdValue); //@todo output this to screen
				}

			}

			if (uploaded && !keepUploaded) {
				files.get(i).delete();
			}
	
		}

		ed.apply();

		return nUploaded;
	}

	private NHFileInfo loadFile(File file) {
		long datasize = file.length();
		byte[] data = new byte[(int) datasize];
		NHFileInfo results = new NHFileInfo();
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {

			in.readFully(data);

			results.data = data;

			ByteBuffer buf = ByteBuffer.wrap(data);
			if(mLittleEndian)
				buf.order(ByteOrder.LITTLE_ENDIAN);
			results.incarnation = ((long)buf.getInt()) & 0xffffffffL;
			results.feature_set = ((long)buf.getInt()) & 0xffffffffL;
			results.entity_count = ((long)buf.getInt()) & 0xffffffffL;
			results.struct_sizes = ((long)buf.getInt()) & 0xffffffffL;

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		results.md5 = getByteMD5(data);

		return results;
	}

	HearseResponse doGet(String baseUrl, String action, List<HearseHeader> headers) throws IOException {
		URL url = new URL(baseUrl + action);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		try {
			conn.setRequestMethod("GET");

			for (HearseHeader h : headers) {
				conn.setRequestProperty(h.getName(), h.getValue());
			}
			conn.setRequestProperty(HEADER_HEARSE_CRC, HEARSE_CRC);
			conn.setRequestProperty(HEADER_CLIENT, CLIENT_ID);

			int responseCode = conn.getResponseCode();
			InputStream is = (responseCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
			byte[] content = (is != null) ? readStream(is) : new byte[0];
			Map<String, List<String>> responseHeaders = conn.getHeaderFields();

			Log.print("Http Get Response code:" + responseCode);
			return new HearseResponse(responseCode, content, responseHeaders);
		} finally {
			conn.disconnect();
		}
	}

	private byte[] readStream(InputStream is) throws IOException {
	        try (InputStream stream = is) {
	                ByteArrayOutputStream bos = new ByteArrayOutputStream();
	                byte[] buffer = new byte[8192];
	                int read;
	                while ((read = stream.read(buffer)) != -1) {
	                        bos.write(buffer, 0, read);
	                }
	                return bos.toByteArray();
	        }
	}
	HearseResponse doPost(String baseUrl, String action, List<HearseHeader> headers, byte[] data) throws IOException {
		URL url = new URL(baseUrl + action);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		try {
			conn.setRequestMethod("POST");
			conn.setDoOutput(true);

			for (HearseHeader h : headers) {
				conn.setRequestProperty(h.getName(), h.getValue());
			}
			conn.setRequestProperty(HEADER_HEARSE_CRC, HEARSE_CRC);
			conn.setRequestProperty(HEADER_CLIENT, CLIENT_ID);

			if (data != null) {
				OutputStream os = conn.getOutputStream();
				try {
					os.write(data);
				} finally {
					os.close();
				}
			}

			int responseCode = conn.getResponseCode();
			InputStream is = (responseCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
			byte[] content = (is != null) ? readStream(is) : new byte[0];
			Map<String, List<String>> responseHeaders = conn.getHeaderFields();

			Log.print("Http Post Response code:" + responseCode);
			return new HearseResponse(responseCode, content, responseHeaders);
		} finally {
			conn.disconnect();
		}
	}

	private boolean isValidBonesFileName(String name) {
		Matcher matcher = PATTERN.matcher(name);

		boolean result = matcher.matches();
		Log.print(name + " is bones:" + result);
		return result;
	}

	void changeUserInfo() throws IOException {
		Log.print("Hearse updating user info: " + userNick + ", " + userEmail);

		List<HearseHeader> headerList = new ArrayList<HearseHeader>();

		headerList.add(new HearseHeader(HEADER_EMAIL, userEmail));

		headerList.add(new HearseHeader(HEADER_NICK, userNick));

		headerList.add(new HearseHeader(HEADER_TOKEN, userToken));

		HearseResponse resp = doGet(BASE_URL, UPDATE_USER, headerList);

		if (resp.getFirstHeader(HEADER_HEARSE) != null && resp.getFirstHeader(HEADER_ERROR) != null) {
			printContent(resp);
		}
	}

	private void printContent(HearseResponse resp) {
	        try (BufferedReader in = new BufferedReader(new InputStreamReader(resp.getInputStream()))) {
	                StringBuilder message = new StringBuilder();
	                String line;
	                while ((line = in.readLine()) != null) {
	                        if(message.length() > 0)
	                                message.append('\n');
	                        message.append(line);
	                }
	                showToast(message.toString());
	        } catch (IOException e) {
	                e.printStackTrace();
	        }
	}
	/**
	 * Method to allow this class to listen for email or nickname changes and send them to Hearse
	 * @param sharedPreferences Preferences
	 * @param key changed key
	 */
	private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			// Java 7 switch would be nice
			boolean enabled = sharedPreferences.getBoolean(PREFS_HEARSE_ENABLE, false);
			boolean enableChange = PREFS_HEARSE_ENABLE.equals(key);
			boolean emailChange = PREFS_HEARSE_MAIL.equals(key);
			boolean nickChange = PREFS_HEARSE_NAME.equals(key);

			if(emailChange || nickChange) {
				sharedPreferences.edit().putBoolean(PREFS_HEARSE_UPDATE_USER, true).apply();
			}

			if(enabled && (emailChange || enableChange)) {
				String mail = sharedPreferences.getString(PREFS_HEARSE_MAIL, "");
				if(mail.length() == 0) {
					showEmailRequired();
				}
			}
		}
	};

	private class NHFileInfo {
	        public byte[] data;
	        String md5;
	        long incarnation;    /* incarnation = major, minor, patchlevel, editlevel */
	        long feature_set;    /* bitmask of config settings */
	        long entity_count;   /* # of monsters and objects */
	        long struct_sizes;   /* size of key structs */

	        public String get1() {
	                return String.valueOf(incarnation);
	        }

	        public String get2() {
	                return String.valueOf(feature_set);
	        }

	        public String get3() {
	                return String.valueOf(entity_count);
	        }

	        public String get4() {
	                return String.valueOf(struct_sizes);
	        }
	}

	static class HearseHeader {
	        private String name;
	        private String value;
	        public HearseHeader(String name, String value) {
	                this.name = name;
	                this.value = value;
	        }
	        public String getName() { return name; }
	        public String getValue() { return value; }
	}

	static class HearseResponse {
	        private int statusCode;
	        private byte[] content;
	        private Map<String, List<String>> headers;
	        public HearseResponse(int statusCode, byte[] content, Map<String, List<String>> headers) {
	                this.statusCode = statusCode;
	                this.content = content;
	                this.headers = headers;
	        }
	        public int getStatusCode() { return statusCode; }
	        public byte[] getContent() { return content; }
	        public String getFirstHeader(String name) {
	                if (name == null) return null;
	                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
	                        if (name.equalsIgnoreCase(entry.getKey())) {
	                                List<String> values = entry.getValue();
	                                return (values != null && !values.isEmpty()) ? values.get(0) : null;
	                        }
	                }
	                return null;
	        }
	        public InputStream getInputStream() {
	                return new ByteArrayInputStream(content);
	        }
	        }
	}
