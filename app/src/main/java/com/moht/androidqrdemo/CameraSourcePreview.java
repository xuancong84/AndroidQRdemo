package com.moht.androidqrdemo;

/*
 * Copyright (C) The Android Open Source Project
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

import android.Manifest;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import androidx.annotation.RequiresPermission;

import com.google.android.gms.common.images.Size;

import java.io.IOException;

import static android.graphics.Bitmap.Config.ARGB_8888;

public class CameraSourcePreview extends ViewGroup {
	private static final String TAG = CameraSourcePreview.class.getSimpleName();

	public static CameraSourcePreview mSelf = null;
	public static Context mContext = null;
	private SurfaceView mSurfaceView;
	private boolean mStartRequested;
	private boolean mSurfaceAvailable;
	private CameraSource mCameraSource;
	public Bitmap bmp_beam = null;
	public SurfaceHolder mOverlayHolder = null;

	public CameraSourcePreview(Context context, AttributeSet attrs) {
		super(context, attrs);
		mSelf = this;
		mContext = context;
		mStartRequested = false;
		mSurfaceAvailable = false;

		mSurfaceView = new SurfaceView(context);
		mSurfaceView.getHolder().addCallback(new SurfaceCallback());
		addView(mSurfaceView);
	}

	@RequiresPermission(Manifest.permission.CAMERA)
	public void start(CameraSource cameraSource) throws IOException, SecurityException {
		if (cameraSource == null)
			stop();

		mCameraSource = cameraSource;

		if (mCameraSource != null) {
			mStartRequested = true;
			startIfReady();
		}
	}

	public void stop() {
		if (mCameraSource != null)
			mCameraSource.stop();
	}

	public void release() {
		if (mCameraSource != null) {
			mCameraSource.release();
			mCameraSource = null;
		}
	}

	// Draw loop for the scanner beam overlay
	private Paint paint = new Paint();
	private int W, H, S, L, T, R, B, D, LEN;
	private long start_time;
	private float [] lines_frame, lines_corner;
	private Rect rect = new Rect();
	private RectF rectF = new RectF();
	private static float period = 8000.0f;	// scan period in milliseconds
	private static float beam_height_factor = 0.1f;	// beam height factor w.r.t square length
	public void drawOverlay() {
		Canvas canvas;
		if ( mOverlayHolder == null )
			return;

		if ( bmp_beam == null ) {
			W = getMeasuredWidth();
			H = getMeasuredHeight();
			S = Math.round( (W < H ? W : H) * 0.8f );
			L = Math.round( W * 0.1f );
			T = Math.round( H * 0.4f - S * 0.5f );
			R = Math.round( W * 0.9f );
			B = Math.round( T + S );
			D = (int)Math.ceil( (W<H?W:H) / 200.0 );
			LEN = Math.round( S / 8.0f );
			lines_frame = new float [] { L, T, R, T, R, T, R, B, L, B, R, B, L, B, L, T };
			lines_corner = new float [] {
					L - D, T - D, L - D + LEN, T - D, L - D, T - D, L - D, T - D + LEN,
					R + D, T - D, R + D - LEN, T - D, R + D, T - D, R + D, T - D + LEN,
					L - D, B + D, L - D + LEN, B + D, L - D, B + D, L - D, B + D - LEN,
					R + D, B + D, R + D - LEN, B + D, R + D, B + D, R + D, B + D - LEN
			};

			// Shift down the torch button
			int vCenter = Math.round( B + ( H - B ) * 0.5f );
			BarcodeCaptureActivity.mTorchButton.layout(Math.round(W*0.4f),
					Math.round(vCenter-W*0.1f),
					Math.round(W*0.6f),
					Math.round(vCenter+W*0.1f));
			BarcodeCaptureActivity.mTorchButton.setVisibility( VISIBLE );

			// Prepare scan beam bitmap
			S -= 2;
			int bw = 32, bh = Math.round( S * beam_height_factor );
			int[] beam_buf = new int[bw * bh];
			float a_value = 1.0f, a_delta = 1.0f / bh;
			for (int y = bh - 1; y >= 0; --y, a_value -= a_delta) {
				int alpha = Math.round(a_value * a_value * 255);
				for (int x = 0; x < bw; ++x) {
					beam_buf[y * bw + x] = (alpha << 24) | 0x00ffff00;
				}
			}
			bmp_beam = Bitmap.createBitmap(beam_buf, bw, bh, ARGB_8888);

			// Initialize other parameters
			start_time = System.currentTimeMillis();
			paint.setXfermode( new PorterDuffXfermode(PorterDuff.Mode.SRC) );
			rect.set(L+1, T+1, R-1, B-1 );

			// Fill the entire surface with a darkened mask
			canvas = mOverlayHolder.lockCanvas();
			canvas.drawColor(0x80000000, PorterDuff.Mode.SRC );

			// Pre-draw 4 corners
			paint.setColor( 0xff00ff00 );
			paint.setStrokeWidth( D * 2 );
			paint.setStrokeCap( Paint.Cap.ROUND );
			canvas.drawLines( lines_corner, paint );

			// Pre-draw the square frame
			paint.setColor( 0xffff0000 );
			paint.setStrokeWidth( 1 );
			paint.setStrokeCap( Paint.Cap.SQUARE );
			canvas.drawLines( lines_frame, paint );
		} else
			canvas = mOverlayHolder.lockCanvas( rect );

		if ( canvas == null )
			return;

		// Draw the totally transparent square scan window
		paint.setColor( 0 );
		canvas.drawRect(L+1, T+1, R-1, B-1, paint );

		// Draw the beam
		float phase = ((System.currentTimeMillis()-start_time)%period)/period;
		float dst_T = phase * S, dst_B = dst_T+S*beam_height_factor;

		paint.setColor( 0xffffffff );
		rectF.set(L+1, T+1+dst_T, R-1, T+1+dst_B );
		canvas.drawBitmap( bmp_beam, null, rectF, paint );

		if( dst_B > S ){	// bottom part overshoot, draw top part
			rectF.top -= S;
			rectF.bottom -= S;
			canvas.drawBitmap( bmp_beam, null, rectF, paint );
		}

		mOverlayHolder.unlockCanvasAndPost( canvas );
	}

	@RequiresPermission(Manifest.permission.CAMERA)
	private void startIfReady() throws IOException, SecurityException {
		if (mStartRequested && mSurfaceAvailable) {
			mCameraSource.start(mSurfaceView.getHolder());
			mStartRequested = false;
		}
	}

	private class SurfaceCallback implements SurfaceHolder.Callback {
		@Override
		public void surfaceCreated(SurfaceHolder surface) {
			mSurfaceAvailable = true;
			try {
				startIfReady();
			} catch (SecurityException se) {
				Log.e(TAG,"Do not have permission to start the camera", se);
			} catch (IOException e) {
				Log.e(TAG, "Could not start camera source.", e);
			}
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder surface) {
			mSurfaceAvailable = false;
		}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		int W = right - left, H = bottom - top;
		CameraSource.mRequestedPreviewWidth = Math.max(W, H);
		CameraSource.mRequestedPreviewHeight = Math.min(W, H);
		for( int x=0, X=getChildCount(); x<X; ++x )
			getChildAt( x ).layout(0,0, W, H );

		try {
			startIfReady();
		} catch (IOException e) {
			Log.e(TAG, "Could not start camera source.", e);
		} catch (SecurityException se) {
			Log.e(TAG, "Does not have permission to start the camera.", se);
		}
	}

	void reLayout( Size size ){
		// Swap width and height sizes when in portrait, since it will be rotated 90 degrees
		int previewHeight = size.getWidth();
		int previewWidth = size.getHeight();

		int viewWidth = getMeasuredWidth();
		int viewHeight = getMeasuredHeight();

		int childWidth, childHeight;
		int childXOffset = 0, childYOffset = 0;
		float widthRatio = (float) viewWidth / (float) previewWidth;
		float heightRatio = (float) viewHeight / (float) previewHeight;

		// To fill the view with the camera preview, while also preserving the correct aspect ratio,
		// it is usually necessary to slightly oversize the child and to crop off portions along one
		// of the dimensions.  We scale up based on the dimension requiring the most correction, and
		// compute a crop offset for the other dimension.
		if (widthRatio > heightRatio) {
			childWidth = viewWidth;
			childHeight = (int) ((float) previewHeight * widthRatio);
			childYOffset = (childHeight - viewHeight) / 2;
		} else {
			childWidth = (int) ((float) previewWidth * heightRatio);
			childHeight = viewHeight;
			childXOffset = (childWidth - viewWidth) / 2;
		}

		// One dimension will be cropped.  We shift child over or up by this offset and adjust
		// the size to maintain the proper aspect ratio.
		for( int x=0, X=getChildCount(); x<X; ++x )
			getChildAt( x ).layout(
				-1 * childXOffset, -1 * childYOffset,
				childWidth - childXOffset, childHeight - childYOffset);
	}
}
