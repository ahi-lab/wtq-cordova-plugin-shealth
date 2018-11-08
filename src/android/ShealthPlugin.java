/**
 * Copyright (C) 2014 Samsung Electronics Co., Ltd. All rights reserved.
 *
 * Mobile Communication Division,
 * Digital Media & Communications Business, Samsung Electronics Co., Ltd.
 *
 * This software and its documentation are confidential and proprietary
 * information of Samsung Electronics Co., Ltd.  No part of the software and
 * documents may be copied, reproduced, transmitted, translated, or reduced to
 * any electronic medium or machine-readable form without the prior written
 * consent of Samsung Electronics.
 *+
 * Samsung Electronics makes no representations with respect to the contents,
 * and assumes no responsibility for any errors that might appear in the
 * software and documents. This publication and the contents hereof are subject
 * to change without notice.
 */

package com.cordova.shealth;

import com.globetrekkerchallenge.app.R;
import com.samsung.android.sdk.healthdata.HealthConnectionErrorResult;
import com.samsung.android.sdk.healthdata.HealthConstants.StepCount;
import com.samsung.android.sdk.healthdata.HealthConstants.Sleep;
import com.samsung.android.sdk.healthdata.HealthDataService;
import com.samsung.android.sdk.healthdata.HealthDataStore;
import com.samsung.android.sdk.healthdata.HealthPermissionManager;
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionKey;
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionResult;
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionType;
import com.samsung.android.sdk.healthdata.HealthResultHolder;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.content.DialogInterface;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import android.content.Context;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ShealthPlugin extends CordovaPlugin {

    public static final String TAG = "StepDiary";
    private HealthDataStore mStore;
    private StepCountReader mReporter;
    private long mCurrentStartTime;
    private Activity actContext;
    private Context appContext;
    private CallbackContext connectCallbackContext;
    private HashMap<String, TimeUnit> TimeUnitLookup;
    private HashMap<TimeUnit, String> TimeUnitRLookup;

    private void fillTimeUnit(TimeUnit t) {
        TimeUnitLookup.put(t.name(), t);
        TimeUnitRLookup.put(t, t.name());
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        actContext = cordova.getActivity();
        appContext = actContext.getApplicationContext();

        // Get the start time of today in local
        mCurrentStartTime = StepCountReader.TODAY_START_UTC_TIME;
        HealthDataService healthDataService = new HealthDataService();
        try {
            healthDataService.initialize(actContext);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Create a HealthDataStore instance and set its listener
        mStore = new HealthDataStore(actContext, mConnectionListener);

        // // Request the connection to the health data store
        mStore.connectService();
        mReporter = new StepCountReader(mStore, actContext);

        cordova.setActivityResultCallback(this);
    }

    @Override
    public void onDestroy() {
        mStore.disconnectService();
        super.onDestroy();
    }

    private final HealthDataStore.ConnectionListener mConnectionListener = new HealthDataStore.ConnectionListener() {
        @Override
        public void onConnected() {
            Log.d(TAG, "onConnected");
            if (isPermissionAcquired()) {
                Log.d(TAG, "Permission is acquired already");
            } else {
                requestPermission();
            }
        }

        @Override
        public void onConnectionFailed(HealthConnectionErrorResult error) {
            Log.d(TAG, "onConnectionFailed");
            showConnectionFailureDialog(error);
        }

        @Override
        public void onDisconnected() {
            Log.d(TAG, "onDisconnected");
            mStore.connectService();

        }
    };
    private void updateBinningData(List<StepCountReader.StepBinningData> stepBinningDataList) {
        // the following code will be replaced with chart drawing code
        Log.d(TAG, "updateBinningChartView");
        // mBinningListAdapter.changeDataSet(stepBinningDataList);
        // for (StepCountReader.StepBinningData data : stepBinningDataList) {
        //     Log.d(TAG, "TIME : " + data.time + "  COUNT : " + data.count);
        // }
    }

    private final HealthResultHolder.ResultListener<PermissionResult> mPermissionListener =
            new HealthResultHolder.ResultListener<PermissionResult>() {

        @Override
        public void onResult(PermissionResult result) {
            Map<PermissionKey, Boolean> resultMap = result.getResultMap();
            // Show a permission alarm and clear step count if permissions are not acquired
            if (resultMap.values().contains(Boolean.FALSE)) {
                //showPermissionAlarmDialog();
                connectCallbackContext.error("Permission was not given");
            }
            else {
                connectCallbackContext.success();
            }
        }
    };

    private void showPermissionAlarmDialog() {
        AlertDialog.Builder alert = new AlertDialog.Builder(cordova.getActivity());
        alert.setTitle("Notice")
                .setMessage("Permission Required")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showConnectionFailureDialog(final HealthConnectionErrorResult error) {

        AlertDialog.Builder alert = new AlertDialog.Builder(cordova.getActivity());

        if (error.hasResolution()) {
            switch (error.getErrorCode()) {
                case HealthConnectionErrorResult.PLATFORM_NOT_INSTALLED:
                    alert.setMessage("Platform is not installed");
                    break;
                case HealthConnectionErrorResult.OLD_VERSION_PLATFORM:
                    alert.setMessage("Requires Upgrade");
                    break;
                case HealthConnectionErrorResult.PLATFORM_DISABLED:
                    alert.setMessage("Platform is disabled");
                    break;
                case HealthConnectionErrorResult.USER_AGREEMENT_NEEDED:
                    alert.setMessage("User agreement needed");
                    break;
                default:
                    alert.setMessage("Connection available");
                    break;
            }
        } else {
            alert.setMessage("Connection not available");
        }

        alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // continue with delete
            }
        });

        if (error.hasResolution()) {
            alert.setNegativeButton("Cancel", null);
        }

        alert.show();
    }

    private boolean isPermissionAcquired() {
        HealthPermissionManager pmsManager = new HealthPermissionManager(mStore);
        try {
            // Check whether the permissions that this application needs are acquired
            Map<PermissionKey, Boolean> resultMap = pmsManager.isPermissionAcquired(generatePermissionKeySet());
            return !resultMap.values().contains(Boolean.FALSE);
        } catch (Exception e) {
            Log.e(TAG, "Permission request fails.", e);
        }
        return false;
    }

    private void requestPermission() {
        HealthPermissionManager pmsManager = new HealthPermissionManager(mStore);
        try {
            // Show user permission UI for allowing user to change options
            pmsManager.requestPermissions(generatePermissionKeySet(), actContext)
                    .setResultListener(mPermissionListener);
        } catch (Exception e) {
            Log.e(TAG, "Permission setting fails.", e);
        }
    }

    private Set<PermissionKey> generatePermissionKeySet() {
        Set<PermissionKey> pmsKeySet = new HashSet<>();
        pmsKeySet.add(new PermissionKey(Sleep.HEALTH_DATA_TYPE, PermissionType.READ));
        pmsKeySet.add(new PermissionKey(StepCount.HEALTH_DATA_TYPE, PermissionType.READ));
        pmsKeySet.add(new PermissionKey(StepCountReader.STEP_SUMMARY_DATA_TYPE_NAME, PermissionType.READ));
        return pmsKeySet;
    }

    /**
     * The "execute" method that Cordova calls whenever the plugin is used from the JavaScript
     * @param action          The action to execute.
     * @param args            The exec() arguments.
     * @param callbackContext The callback context used when calling back into JavaScript.
     * @return
     * @throws JSONException
     */
    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {

        // Select the getData: get Datasets+Datapoints from GoogleFit according to the query parameters
        if ("getData".equals(action)) {

            long st = args.getJSONObject(0).getLong("startTime");
            long et = args.getJSONObject(0).getLong("endTime");

            //cordova.getThreadPool().execute( new GetStuff(queryData(st, et, dt), callbackContext));
            mReporter.requestDailyStepCount(st, callbackContext);
        } else if ("getSleepData".equals(action)) {
            long st = args.getJSONObject(0).getLong("startTime");
            mReporter.requestDailySleep(st, callbackContext);
        } else if ("connect".equals(action)) {
            connectCallbackContext = callbackContext;
            // Request the connection to the health data store
            if (isPermissionAcquired()) {
                callbackContext.success();
            } else {
                requestPermission();
            }
        }

        return true;  // Returning false will result in a "MethodNotFound" error.
    }
}
