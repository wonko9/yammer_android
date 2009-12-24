package com.yammer.v1;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import android.os.Process;
import java.util.LinkedList;

import javax.net.ssl.HttpsURLConnection;

import org.apache.http.conn.ssl.AllowAllHostnameVerifier;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class BitmapDownloader {

	public static String TAG_BITMAPDOWNLOADER = "ImageDownloader";
	private Bitmap defaultBitmap = null;
	private Context context = null;
	private int concurrentDownloads = 2;
	private final LinkedList<Runnable> queue;
	private final PoolWorker[] threads;

	private class PoolWorker extends Thread {
		public void run() {
			if (G.DEBUG_BMDOWNLOADER) Log.i(TAG_BITMAPDOWNLOADER, "Starting poolworker thread");
			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
			Runnable r;
			while(true) {
				synchronized(queue) {
					while( queue.isEmpty() ) {
						try  {
							queue.wait();
						} catch (InterruptedException ignored) {
							// 
						}
					}
					r = (Runnable)queue.removeFirst();
				}
				if (G.DEBUG_BMDOWNLOADER) Log.d(TAG_BITMAPDOWNLOADER, "Starting download thread: " + r);
				try {
					r.run();
				} catch (RuntimeException e) {
					// We need to catch runtimeexception - otherwise
					// we could leak threads
					if (G.DEBUG_BMDOWNLOADER) Log.e(TAG_BITMAPDOWNLOADER, "ERROR: " + e.getMessage());
				}
			}
		}
	};
	
	BitmapDownloader(Context context) {
		if (G.DEBUG_BMDOWNLOADER) Log.d(TAG_BITMAPDOWNLOADER, "BitmapDownloader::BitmapDownloader");
		this.context = context;
		queue = new LinkedList<Runnable>();
		threads = new PoolWorker[concurrentDownloads];
		// Create worker threads
		for ( int i=0; i<concurrentDownloads; i++ ) {
			threads[i] = new PoolWorker();
			threads[i].start();
		}
		// TODO: Remove all temporary files	    
	}
	
	/**
	 * Get the default bitmap, when there is no bitmap in the cache
	 * @return
	 */
	protected Bitmap getDefaultBitmap() {
		// Create the default bitmap if it doesn't exist
		if ( defaultBitmap == null ) {
			defaultBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_photo_small);			
		}
		return defaultBitmap;
	}
	
	/**
	 * Check that file identified by MD5 is not downloading already
	 */
	protected boolean isDownloading(String md5) {
		if (G.DEBUG_BMDOWNLOADER) Log.d(TAG_BITMAPDOWNLOADER, "BitmapDownloader::isDownloading");
		// Try to open the file
	    File file = new File(context.getFilesDir(), md5 + ".tmp");
		if ( file.exists() ) {
			// It exists, so we're probably downloading it right now
			return true;
		} 
		return false;
	}

	private void startDownload(String urlString, String filename) {
		if (G.DEBUG_BMDOWNLOADER) Log.d(TAG_BITMAPDOWNLOADER, "BitmapDownloader::startDownload");
		try {
			URL url = new URL(urlString);
			HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
			connection.setHostnameVerifier(new AllowAllHostnameVerifier());
			InputStream is = connection.getInputStream();
			BufferedInputStream bis = new BufferedInputStream(is);
			FileOutputStream fos = context.openFileOutput(filename+".tmp", 0);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			// Dump input stream to output stream
		    while (true) {
		        int data = bis.read();
		        if (data == -1)
		          break;
		        bos.write(data);
		      }
		    bos.flush();
		    // Close up everything
		    bis.close();
		    bos.close();
		    // Rename the downloaded file
		    if (G.DEBUG_BMDOWNLOADER) Log.d(TAG_BITMAPDOWNLOADER, "Renaming downloaded temporary file");
		    File srcFile = new File(context.getFilesDir(), filename + ".tmp");
		    File dstFile = new File(context.getFilesDir(), filename );
		    srcFile.renameTo(dstFile);
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			if (G.DEBUG_BMDOWNLOADER) Log.i(TAG_BITMAPDOWNLOADER, "Removing temporary file: " + filename + ".tmp");
		}
	}
	
	/**
	 * Retrieve and return the image pointed to by the url
	 *
	 * @param url
	 * @return
	 */
	// TODO: Check that it is actually a bitmap being pointed to
	public Bitmap getBitmap(final String urlString, final String md5) {
		if (G.DEBUG_BMDOWNLOADER) Log.d(TAG_BITMAPDOWNLOADER, "BitmapDownloader::getBitmap");
		// Bitmat to return
		Bitmap bm = null;
		// Create an MD5 of the URL string to easily identify the image
		if (G.DEBUG_BMDOWNLOADER) Log.d(TAG_BITMAPDOWNLOADER, "MD5Gen.md5("+urlString+"):"+md5);
		// Check if we have the image in the cache - attempt to load it
		bm = BitmapFactory.decodeFile(context.getFilesDir() + "/" + md5);
		if ( bm != null ) {
			// Bitmap was found and decoded, so return it
			return bm;
		}
		// Are we already downloading it?
		if ( isDownloading(md5) ) {
			if (G.DEBUG_BMDOWNLOADER) Log.d(TAG_BITMAPDOWNLOADER, "Returning default bitmap");
			return getDefaultBitmap();
		}
		// Not downloading, so initiate the download
		Runnable downloader = new Runnable() {
			public void run() {
				startDownload(urlString, md5);				
			}
		};
		if (G.DEBUG_BMDOWNLOADER) Log.d(TAG_BITMAPDOWNLOADER, "Adding downloader to queue");
		synchronized(queue) {
			queue.addLast(downloader);
			queue.notify();
		}
		// Return the default bitmap
		return getDefaultBitmap();		
	}
}
