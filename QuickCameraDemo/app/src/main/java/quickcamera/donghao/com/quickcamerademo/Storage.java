/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package quickcamera.donghao.com.quickcamerademo;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.location.Location;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

public class Storage {

	private static final String TAG = "CameraStorage";

	public static final String DCIM = Environment
			.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
			.toString();

	public static final String DIRECTORY = DCIM + "/Camera";

	public static final String RAW_DIRECTORY = DCIM + "/Camera/raw";

	// luchengjian@basewin +{ 20121218
	public static final String EMMC_DCIM = Environment
			.getExternalStorageDirectory().toString();

	public static final String EMMC_DIRECTORY = EMMC_DCIM + "/DCIM/Camera";

	public static final String EMMC_RAW_DIRECTORY = EMMC_DCIM
			+ "/DCIM/Camera/raw";
	// luchengjian@basewin +} 20121218

	// Match the code in MediaProvider.computeBucketValues().
	public static final String BUCKET_ID = String.valueOf(DIRECTORY
			.toLowerCase().hashCode());
	public static final String CAMERA_RAW_IMAGE_BUCKET_ID = String
			.valueOf(RAW_DIRECTORY.toLowerCase().hashCode());

	public static final long UNAVAILABLE = -1L;
	public static final long PREPARING = -2L;
	public static final long UNKNOWN_SIZE = -3L;
	public static final long LOW_STORAGE_THRESHOLD = 5000000;
	public static final long PICTURE_SIZE = 1500000;

	private static final int BUFSIZE = 4096;

	public static Uri addImage(ContentResolver resolver, String title,
							   String pictureFormat, long date, Location location,
							   int orientation, byte[] jpeg, int width, int height) {
		// Save the image.
		String directory = null;
		String ext = null;
		if (pictureFormat == null || pictureFormat.equalsIgnoreCase("jpeg")) {
			ext = ".jpg";
			directory = DIRECTORY;
		} else if (pictureFormat.equalsIgnoreCase("raw")) {
			ext = ".raw";
			directory = RAW_DIRECTORY;
		} else if (pictureFormat.equalsIgnoreCase("yuv420sp")) {
			ext = ".jpg";
			directory = DIRECTORY;
		} else {
			Log.e(TAG, "Invalid pictureFormat " + pictureFormat);
			return null;
		}

		String path = directory + '/' + title + ext;
		if (null != pictureFormat && pictureFormat.equalsIgnoreCase("yuv420sp")) {
			BufferedOutputStream outJpeg = null;
			try {
				File dir = new File(directory);
				if (!dir.exists()) {
					dir.mkdirs();
				}

				outJpeg = new BufferedOutputStream(new FileOutputStream(path));
				YuvImage yuv = new YuvImage(jpeg, ImageFormat.NV21, width,
						height, null);
				Rect jpegRect = new Rect(0, 0, width, height);
				yuv.compressToJpeg(jpegRect, 100, outJpeg);
				outJpeg.flush();
			} catch (Exception e) {
				return null;
			} finally {
				try {
					outJpeg.close();
				} catch (Exception e) {
				}
			}

		} else {
			FileOutputStream out = null;
			try {
				File dir = new File(directory);
				if (!dir.exists())
					dir.mkdirs();
				out = new FileOutputStream(path);
				out.write(jpeg);
			} catch (Exception e) {
				Log.e(TAG, "Failed to write image", e);
				return null;
			} finally {
				try {
					out.close();
				} catch (Exception e) {
				}
			}
		}
		// Insert into MediaStore.
		ContentValues values = new ContentValues(9);
		values.put(ImageColumns.TITLE, title);
		values.put(ImageColumns.DISPLAY_NAME, title + ext);
		values.put(ImageColumns.DATE_TAKEN, date);
		values.put(ImageColumns.MIME_TYPE, "image/jpeg");
		values.put(ImageColumns.ORIENTATION, orientation);
		values.put(ImageColumns.DATA, path);
		values.put(ImageColumns.SIZE, jpeg.length);
		values.put(ImageColumns.WIDTH, width);
		values.put(ImageColumns.HEIGHT, height);

		if (location != null) {
			values.put(ImageColumns.LATITUDE, location.getLatitude());
			values.put(ImageColumns.LONGITUDE, location.getLongitude());
		}

		Uri uri = null;
		try {
			uri = resolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values);
		} catch (Throwable th) {
			Log.e(TAG, "Failed to write MediaStore" + th);
		}
		return uri;
	}


}
