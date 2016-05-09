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

package com.iqoo.secure.safeguard.extractthumbnail;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

import com.iqoo.secure.filemanager.FileUtils;
import com.iqoo.secure.safeguard.extractthumbnail.BlobCache.LookupRequest;

public class CacheManager {
    private static final String TAG = "CacheManager";
    private static final String KEY_CACHE_UP_TO_DATE = "cache-up-to-date";
    private static final String IMAGE_CACHE_FILE = "imgcache";
    private static final int IMAGE_CACHE_MAX_ENTRIES = 5000;
    private static final int IMAGE_CACHE_MAX_BYTES = 200 * 1024 * 1024;
    private static final int IMAGE_CACHE_VERSION = 7;
    private static boolean sOldCheckDone = false;
    private BlobCache mCache;
    private static final Object mLock = new Object();

    public CacheManager(Context cxt) {
		// TODO Auto-generated constructor stub
    	mCache = getCache(cxt, IMAGE_CACHE_FILE, IMAGE_CACHE_MAX_ENTRIES, IMAGE_CACHE_MAX_BYTES, IMAGE_CACHE_VERSION);
	}
    
    public BlobCache getCache(Context context, String filename,
            int maxEntries, int maxBytes, int version) {
    	BlobCache cache = null;
    	synchronized (mLock) {
            if (!sOldCheckDone) {
                removeOldFilesIfNecessary(context);
                sOldCheckDone = true;
            }
            
            File cacheDir = context.getExternalCacheDir();
            String path = cacheDir.getAbsolutePath() + "/" + filename;
            try {
                cache = new BlobCache(path, maxEntries, maxBytes, false,
                        version);
            } catch (IOException e) {
                Log.e(TAG, "Cannot instantiate cache!", e);
            }
		}
        return cache;
    }

    public Bitmap extractThumbnail(String path, int size, int type) {
    	Bitmap bitmap = null;
    	BitmapFactory.Options options = new BitmapFactory.Options();
    	options.inPreferredConfig = Bitmap.Config.RGB_565;

    	if (FileUtils.FileType.IMAGE == type) {
    		bitmap = ExtractThumbnailUtils.decodeThumbnail(path, options, size);	
    	} else if (FileUtils.FileType.VIDEO == type){
    		bitmap = ExtractThumbnailUtils.createVideoThumbnail(path);
    	}
        
        if (bitmap == null) {
            Log.w(TAG, "extractThumbnail failed ");
            return null;
        } else {
        	bitmap = ExtractThumbnailUtils.resizeDownBySideLength(bitmap, size, true);
        }
        
        byte[] array = ExtractThumbnailUtils.compressToBytes(bitmap);
        putImageData(path, array);
	
		return bitmap;
    }
    
    public boolean getImageData(String path, BytesBuffer buffer) {
    	if (mCache == null) {
    		return false;
    	}
    	
        byte[] key = makeKey(path);
        long cacheKey = ExtractThumbnailUtils.crc64Long(key);
        try {
            LookupRequest request = new LookupRequest();
            request.key = cacheKey;
            request.buffer = buffer.data;
            synchronized (mLock) {
                if (!mCache.lookup(request)) return false;
            }
            if (isSameKey(key, request.buffer)) {
                buffer.data = request.buffer;
                buffer.offset = key.length;
                buffer.length = request.length - buffer.offset;
                return true;
            }
        } catch (IOException ex) {
            // ignore.
        }
        return false;
    }
    
    public void putImageData(String path, byte[] value) {
    	if (mCache == null) {
    		return;
    	}
        byte[] key = makeKey(path);
        long cacheKey = ExtractThumbnailUtils.crc64Long(key);
        ByteBuffer buffer = ByteBuffer.allocate(key.length + value.length);
        buffer.put(key);
        buffer.put(value);
        synchronized (mLock) {
            try {
                mCache.insert(cacheKey, buffer.array());
            } catch (IOException ex) {
                // ignore.
            }
        }
    }
    
    public void close() {
    	if (mCache == null) {
    		synchronized (mLock) {
        		mCache.close();
        		mCache = null;
			}
    	}
    }
    
    private static byte[] makeKey(String path) {
        return ExtractThumbnailUtils.getBytes(path.toString());
    }

    private static boolean isSameKey(byte[] key, byte[] buffer) {
        int n = key.length;
        if (buffer.length < n) {
            return false;
        }
        for (int i = 0; i < n; ++i) {
            if (key[i] != buffer[i]) {
                return false;
            }
        }
        return true;
    }
    
    // Removes the old files if the data is wiped.
    private static void removeOldFilesIfNecessary(Context context) {
        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(context);
        int n = 0;
        try {
            n = pref.getInt(KEY_CACHE_UP_TO_DATE, 0);
        } catch (Throwable t) {
            // ignore.
        }
        if (n != 0) return;
        pref.edit().putInt(KEY_CACHE_UP_TO_DATE, 1).commit();

        File cacheDir = context.getExternalCacheDir();
        String prefix = cacheDir.getAbsolutePath() + "/";

        BlobCache.deleteFiles(prefix + "imgcache");
    }
}
