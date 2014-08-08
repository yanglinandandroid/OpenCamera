/*
The contents of this file are subject to the Mozilla Public License
Version 1.1 (the "License"); you may not use this file except in
compliance with the License. You may obtain a copy of the License at
http://www.mozilla.org/MPL/

Software distributed under the License is distributed on an "AS IS"
basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
License for the specific language governing rights and limitations
under the License.

The Original Code is collection of files collectively known as Open Camera.

The Initial Developer of the Original Code is Almalence Inc.
Portions created by Initial Developer are Copyright (C) 2013 
by Almalence Inc. All Rights Reserved.
 */

package com.almalence.plugins.capture.multishot;

import java.nio.ByteBuffer;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.os.CountDownTimer;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

/* <!-- +++
 import com.almalence.opencam_plus.CameraController;
 import com.almalence.opencam_plus.MainScreen;
 import com.almalence.opencam_plus.PluginCapture;
 import com.almalence.opencam_plus.PluginManager;
 import com.almalence.opencam_plus.R;
 +++ --> */
// <!-- -+-
import com.almalence.opencam.cameracontroller.CameraController;
import com.almalence.opencam.MainScreen;
import com.almalence.opencam.PluginCapture;
import com.almalence.opencam.PluginManager;
import com.almalence.opencam.R;
//-+- -->
import com.almalence.SwapHeap;
import com.almalence.YuvImage;

/***
 * Implements group shot capture plugin - captures predefined number of images
 ***/

public class MultiShotCapturePlugin extends PluginCapture
{

	private static final String	TAG					= "MultiShotCapturePlugin";

	// defaul val. value should come from config
	private int					imageAmount			= 1;
	private int					pauseBetweenShots	= 0;
	private int					imagesTaken			= 0;

	public MultiShotCapturePlugin()
	{
		super("com.almalence.plugins.multishotcapture", R.xml.preferences_capture_multishot, 0, 0, null);
	}

	@Override
	public void onResume()
	{
		takingAlready = false;
		imagesTaken = 0;
		inCapture = false;

		refreshPreferences();

		MainScreen.setCaptureYUVFrames(true);
	}

	@Override
	public void onGUICreate()
	{
		MainScreen.getGUIManager().showHelp(MainScreen.getInstance().getString(R.string.MultiShot_Help_Header),
				MainScreen.getInstance().getResources().getString(R.string.MultiShot_Help),
				R.drawable.plugin_help_object, "multiShotShowHelp");
	}

	private void refreshPreferences()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainScreen.getMainContext());
		imageAmount = Integer.parseInt(prefs.getString("multiShotImagesAmount", "7"));
		pauseBetweenShots = Integer.parseInt(prefs.getString("multiShotPauseBetweenShots", "750"));
	}

	public boolean delayedCaptureSupported()
	{
		return true;
	}

	public void takePicture()
	{
		if (!inCapture)
		{
			inCapture = true;
			takingAlready = true;
			refreshPreferences();
			
			try
			{
				requestID = CameraController.captureImagesWithParams(imageAmount, CameraController.YUV, pauseBetweenShots, null);
			} catch (Exception e)
			{
				e.printStackTrace();
				Log.e(TAG, "CameraController.captureImage failed: " + e.getMessage());
				inCapture = false;
				takingAlready = false;
				PluginManager.getInstance().sendMessage(PluginManager.MSG_BROADCAST, 
						PluginManager.MSG_CONTROL_UNLOCKED);
				MainScreen.getGUIManager().lockControls = false;
			}
		}
	}


	@Override
	public void onImageTaken(int frame, byte[] frameData, int frame_len, boolean isYUV)
	{
		imagesTaken++;

		if (frame == 0)
		{
			Log.i(TAG, "Load to heap failed");

			PluginManager.getInstance().sendMessage(PluginManager.MSG_CAPTURE_FINISHED, 
					String.valueOf(SessionID));

			imagesTaken = 0;
			MainScreen.getInstance().muteShutter(false);
			inCapture = false;
			return;
		}
		String frameName = "frame" + imagesTaken;
		String frameLengthName = "framelen" + imagesTaken;

		PluginManager.getInstance().addToSharedMem(frameName + SessionID, String.valueOf(frame));
		PluginManager.getInstance().addToSharedMem(frameLengthName + SessionID, String.valueOf(frame_len));
		PluginManager.getInstance().addToSharedMem("frameorientation" + imagesTaken + SessionID,
				String.valueOf(MainScreen.getGUIManager().getDisplayOrientation()));
		PluginManager.getInstance().addToSharedMem("framemirrored" + imagesTaken + SessionID,
				String.valueOf(CameraController.isFrontCamera()));

		PluginManager.getInstance().addToSharedMem("isyuv" + SessionID, String.valueOf(isYUV));

		if (imagesTaken == 1 && !isYUV)
			PluginManager.getInstance().addToSharedMemExifTagsFromJPEG(frameData, SessionID, -1);
		try
		{
			CameraController.startCameraPreview();
		} catch (RuntimeException e)
		{
			Log.i(TAG, "StartPreview fail");

			PluginManager.getInstance().sendMessage(PluginManager.MSG_CAPTURE_FINISHED, 
					String.valueOf(SessionID));

			imagesTaken = 0;
			MainScreen.getInstance().muteShutter(false);
			inCapture = false;
			return;
		}
		if (imagesTaken >= imageAmount)
		{
			PluginManager.getInstance().addToSharedMem("amountofcapturedframes" + SessionID,
					String.valueOf(imagesTaken));

			PluginManager.getInstance().sendMessage(PluginManager.MSG_CAPTURE_FINISHED, 
					String.valueOf(SessionID));

			imagesTaken = 0;
			new CountDownTimer(5000, 5000)
			{
				public void onTick(long millisUntilFinished)
				{
				}

				public void onFinish()
				{
					inCapture = false;
				}
			}.start();
		}
		takingAlready = false;		
	}

	@TargetApi(21)
	@Override
	public void onCaptureCompleted(CaptureResult result)
	{
		if (result.getSequenceId() == requestID)
		{
			if (imagesTaken == 1)
				PluginManager.getInstance().addToSharedMemExifTagsFromCaptureResult(result, SessionID);
		}
	}

	@Override
	public void onAutoFocus(boolean paramBoolean)
	{
		if (takingAlready)
			takePicture();
	}

	@Override
	public void onPreviewFrame(byte[] data)
	{
	}
}