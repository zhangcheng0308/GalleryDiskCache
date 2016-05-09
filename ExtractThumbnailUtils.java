package com.iqoo.secure.safeguard.extractthumbnail;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory.Options;
import android.os.Build;
import android.util.FloatMath;
import android.util.Log;

public class ExtractThumbnailUtils {
	private static final String TAG = "ExtractThumbnailUtils";
	private static final int DEFAULT_JPEG_QUALITY = 90;
	private static final long INITIALCRC = 0xFFFFFFFFFFFFFFFFL;
	private static long[] sCrcTable = new long[256];
	private static final long POLY64REV = 0x95AC9329AC4BC9B5L;
    static {
        // http://bioinf.cs.ucl.ac.uk/downloads/crc64/crc64.c
        long part;
        for (int i = 0; i < 256; i++) {
            part = i;
            for (int j = 0; j < 8; j++) {
                long x = ((int) part & 1) != 0 ? POLY64REV : 0;
                part = (part >> 1) ^ x;
            }
            sCrcTable[i] = part;
        }
    }
    
    public static final long crc64Long(byte[] buffer) {
        long crc = INITIALCRC;
        for (int k = 0, n = buffer.length; k < n; ++k) {
            crc = sCrcTable[(((int) crc) ^ buffer[k]) & 0xff] ^ (crc >> 8);
        }
        return crc;
    }
    
    public static byte[] getBytes(String in) {
        byte[] result = new byte[in.length() * 2];
        int output = 0;
        for (char ch : in.toCharArray()) {
            result[output++] = (byte) (ch & 0xFF);
            result[output++] = (byte) (ch >> 8);
        }
        return result;
    }
    
    public static Bitmap decodeThumbnail(String filePath, Options options, int targetSize) {
    	Bitmap bmp = null;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(filePath);
            FileDescriptor fd = fis.getFD();
            bmp = decodeThumbnail(fd, options, targetSize);
        } catch (Exception ex) {
            Log.w(TAG, ex);
            return null;
        } finally {
            if (fis != null) {
            	try {
					fis.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
        }
    	return bmp;
    }
    
    private static Bitmap decodeThumbnail(FileDescriptor fd, Options options, int targetSize) {
    	Bitmap bitmap = null;
    	if (options == null) options = new Options();
    	options.inJustDecodeBounds = true;
    	BitmapFactory.decodeFileDescriptor(fd, null, options);
    	
        int w = options.outWidth;
        int h = options.outHeight;
        System.out.println("zhangcheng 111 decodeThumbnail w " + w + "--h " + h);
        float scale = (float) targetSize / Math.max(w, h);
        options.inSampleSize = computeSampleSizeLarger(scale);
        System.out.println("zhangcheng decodeThumbnail scale " + scale + "==sampleSize " + options.inSampleSize);
        options.inJustDecodeBounds = false;
        
        bitmap = BitmapFactory.decodeFileDescriptor(fd, null, options);
        if (bitmap == null) return null;
        
        scale = (float) targetSize / Math.max(bitmap.getWidth(), bitmap.getHeight());
//        if (scale <= 0.5) bitmap = resizeBitmapByScale(bitmap, scale, true);
        return bitmap;
    }
    
    private static int computeSampleSizeLarger(float scale) {
        int initialSize = (int) FloatMath.floor(1f / scale);
        if (initialSize <= 1) return 1;

        return initialSize <= 8
                ? prevPowerOf2(initialSize)
                : initialSize / 8 * 8;
    }
    
    private static int prevPowerOf2(int n) {
        if (n <= 0) throw new IllegalArgumentException();
        return Integer.highestOneBit(n);
    }
    
    private static Bitmap resizeBitmapByScale(
            Bitmap bitmap, float scale, boolean recycle) {
        int width = Math.round(bitmap.getWidth() * scale);
        int height = Math.round(bitmap.getHeight() * scale);
        System.out.println("zhangcheng--resizeBitmapByScale bmp width " + bitmap.getWidth() + "=>>=height " + bitmap.getHeight());
        System.out.println("zhangcheng--resizeBitmapByScale width " + width + "==height " + height + "--scale " + scale);
        if (width == bitmap.getWidth()
                && height == bitmap.getHeight()) return bitmap;
        Bitmap target = Bitmap.createBitmap(width, height, getConfig(bitmap));
        Canvas canvas = new Canvas(target);
        canvas.scale(scale, scale);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        if (recycle) bitmap.recycle();
        return target;
    }
    
    private static Bitmap.Config getConfig(Bitmap bitmap) {
        Bitmap.Config config = bitmap.getConfig();
        if (config == null) {
            config = Bitmap.Config.RGB_565;
        }
        return config;
    }
    
    public static Bitmap resizeDownBySideLength(
            Bitmap bitmap, int maxLength, boolean recycle) {
        int srcWidth = bitmap.getWidth();
        int srcHeight = bitmap.getHeight();
        System.out.println("zhangcheng  resizeDownBySideLength srcWidth " + srcWidth + "==srcHeight " + srcHeight + "** maxLength " + maxLength);
        float scale = Math.max(
                (float) maxLength / srcWidth, (float) maxLength / srcHeight);
        System.out.println("zhangcheng>>>>>>>>>>> resizeDownBySideLength scale " + scale);
        if (scale >= 1.0f) return bitmap;
        return resizeBitmapByScale(bitmap, scale, recycle);
    }
    
    public static byte[] compressToBytes(Bitmap bitmap) {
        return compressToBytes(bitmap, DEFAULT_JPEG_QUALITY);
    }
    
    private static byte[] compressToBytes(Bitmap bitmap, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(CompressFormat.JPEG, quality, baos);
        return baos.toByteArray();
    }
    
    public static Bitmap decodeUsingPool(byte[] data, int offset,
            int length, BitmapFactory.Options options) {
    	Bitmap bitmap = null;
    	
        if (options == null) options = new BitmapFactory.Options();
        if (options.inSampleSize < 1) options.inSampleSize = 1;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        bitmap = BitmapFactory.decodeByteArray(data, offset, length, options);
        
        return bitmap;
    }
    
    public static Bitmap createVideoThumbnail(String filePath) {
        // MediaMetadataRetriever is available on API Level 8
        // but is hidden until API Level 10
        Class<?> clazz = null;
        Object instance = null;
        try {
            clazz = Class.forName("android.media.MediaMetadataRetriever");
            instance = clazz.newInstance();

            Method method = clazz.getMethod("setDataSource", String.class);
            method.invoke(instance, filePath);

            // The method name changes between API Level 9 and 10.
            if (Build.VERSION.SDK_INT <= 9) {
                return (Bitmap) clazz.getMethod("captureFrame").invoke(instance);
            } else {
                byte[] data = (byte[]) clazz.getMethod("getEmbeddedPicture").invoke(instance);
                if (data != null) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (bitmap != null) return bitmap;
                }
                return (Bitmap) clazz.getMethod("getFrameAtTime").invoke(instance);
            }
        } catch (IllegalArgumentException ex) {
            // Assume this is a corrupt video file
        } catch (RuntimeException ex) {
            // Assume this is a corrupt video file.
        } catch (InstantiationException e) {
            Log.e(TAG, "createVideoThumbnail", e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "createVideoThumbnail", e);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "createVideoThumbnail", e);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "createVideoThumbnail", e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "createVideoThumbnail", e);
        } finally {
            try {
                if (instance != null) {
                    clazz.getMethod("release").invoke(instance);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
