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
 *
 * Samsung Electronics makes no representations with respect to the contents,
 * and assumes no responsibility for any errors that might appear in the
 * software and documents. This publication and the contents hereof are subject
 * to change without notice.
 */

package com.cordova.shealth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.database.Cursor;

import org.apache.cordova.CallbackContext;
import com.samsung.android.sdk.healthdata.HealthConstants;
import com.samsung.android.sdk.healthdata.HealthData;
import com.samsung.android.sdk.healthdata.HealthConstants.Sleep;
import com.samsung.android.sdk.healthdata.HealthDataResolver;
import com.samsung.android.sdk.healthdata.HealthDataResolver.AggregateRequest;
import com.samsung.android.sdk.healthdata.HealthDataResolver.AggregateRequest.AggregateFunction;
import com.samsung.android.sdk.healthdata.HealthDataResolver.AggregateRequest.TimeGroupUnit;
import com.samsung.android.sdk.healthdata.HealthDataResolver.Filter;
import com.samsung.android.sdk.healthdata.HealthDataResolver.ReadRequest;
import com.samsung.android.sdk.healthdata.HealthDataResolver.SortOrder;
import com.samsung.android.sdk.healthdata.HealthDataStore;
import com.samsung.android.sdk.healthdata.HealthDataUtil;
import com.samsung.android.sdk.healthdata.HealthConnectionErrorResult;
import com.samsung.android.sdk.healthdata.HealthConstants.StepCount;
import com.samsung.android.sdk.healthdata.HealthDataService;
import com.samsung.android.sdk.healthdata.HealthDataStore;
import com.samsung.android.sdk.healthdata.HealthPermissionManager;
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionKey;
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionResult;
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionType;
import com.samsung.android.sdk.healthdata.HealthResultHolder;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import org.json.JSONException;
import org.json.JSONObject;


public class StepCountReader {

    public static final String STEP_SUMMARY_DATA_TYPE_NAME = "com.samsung.shealth.step_daily_trend";
    public static final String SLEEP_DATA_TYPE_NAME = "com.samsung.health.sleep";

    public static final long TODAY_START_UTC_TIME;
    public static final long ONE_DAY = 24 * 60 * 60 * 1000;

    private static final String PROPERTY_TIME = "day_time";
    private static final String PROPERTY_COUNT = "count";
    private static final String PROPERTY_BINNING_DATA = "binning_data";
    private static final String ALIAS_TOTAL_COUNT = "count";
    private static final String ALIAS_DEVICE_UUID = "deviceuuid";
    private static final String ALIAS_BINNING_TIME = "binning_time";

    private final HealthDataResolver mResolver;
    private final Activity currentActivity;
    static {
        TODAY_START_UTC_TIME = getTodayStartUtcTime();
    }

    private static long getTodayStartUtcTime() {
        Calendar today = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        Log.d("MainActivity", "Today : " + today.getTimeInMillis());

        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        return today.getTimeInMillis();
    }

    public StepCountReader(HealthDataStore store, Activity activity) {
        mResolver = new HealthDataResolver(store, null);
        currentActivity = activity;
    }

    // Get the daily total step count of a specified day
    public void requestDailyStepCount(long startTime, final CallbackContext callbackContext) {
            // Get historical step count
            readStepDailyTrend(startTime, callbackContext);
    }
    public void requestDailySleep(long startTime, final CallbackContext callbackContext) {
        // Get historical sleep
        Filter filter = Filter.and(Filter.greaterThanEquals(Sleep.END_TIME, startTime));
                // filtering source type "combined(-2)"


        ReadRequest request = new ReadRequest.Builder()
                .setDataType(SLEEP_DATA_TYPE_NAME)
                .setFilter(filter)
                .build();

        try {
            mResolver.read(request).setResultListener(result -> {


                int totalCount = 0;
                JSONArray sleepResponse = new JSONArray();
                List<StepBinningData> binningDataList = Collections.emptyList();
                Cursor c = null;
                try {
                    c = result.getResultCursor();
                    if (c != null) {
                        while(c.moveToNext()) {

                            long startSleepTime = c.getLong(c.getColumnIndex(Sleep.START_TIME));
                            long endSleepTime = c.getLong(c.getColumnIndex(Sleep.END_TIME));
                            long sleepOffset = c.getLong(c.getColumnIndex(Sleep.TIME_OFFSET));
                            //Log.d("date", dateFormat.format(dayTime));
                            Log.d("count", "Step Count: " + startSleepTime);
                            JSONObject daySleep = new JSONObject();
                            daySleep.put("startTime", startSleepTime);
                            daySleep.put("endSleepTime", endSleepTime);
                            daySleep.put("offset", sleepOffset);
                            sleepResponse.put(daySleep);
                        }
                    } else {
                        Log.d("cursor", "The cursor is null.");
                    }
                }
                catch(Exception e) {
                    Log.e("message", e.getClass().getName() + " - " + e.getMessage());
                }
                finally {
                    if (c != null) {
                        c.close();
                    }
                }
                if(callbackContext != null) {
                    callbackContext.success(sleepResponse);
                }

            });
        } catch (Exception e) {
            JSONArray stepResponse = new JSONArray();
            Log.e("StepCounterReader", "Getting daily step trend fails.", e);
            callbackContext.success(stepResponse);
        }
    }

    private void readStepDailyTrend(final long startTime, final CallbackContext callbackContext) {

        Filter filter = Filter.and(Filter.greaterThanEquals(PROPERTY_TIME, startTime),
                // filtering source type "combined(-2)"
                Filter.eq("source_type", -2));

        ReadRequest request = new ReadRequest.Builder()
                .setDataType(STEP_SUMMARY_DATA_TYPE_NAME)
                .setSort("day_time", HealthDataResolver.SortOrder.DESC)
                .setFilter(filter)
                .build();

        try {
            mResolver.read(request).setResultListener(result -> {
                int totalCount = 0;

                JSONArray stepResponse = new JSONArray();
                List<StepBinningData> binningDataList = Collections.emptyList();
                Cursor c = null;
                try {
                    c = result.getResultCursor();
                    if (c != null) {
                        while(c.moveToNext()) {

                            long dayTime = c.getLong(c.getColumnIndex("day_time"));
                            int stepCount = c.getInt(c.getColumnIndex("count"));


                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
                            //Log.d("date", dateFormat.format(dayTime));
                            Log.d("count", "Step Count: " + stepCount);
                            JSONObject daySteps = new JSONObject();
                            daySteps.put("date", dayTime);
                            daySteps.put("stepCount", stepCount);
                            stepResponse.put(daySteps);
                        }
                    } else {
                        Log.d("cursor", "The cursor is null.");
                    }
                }
                catch(Exception e) {
                    Log.e("message", e.getClass().getName() + " - " + e.getMessage());
                }
                finally {
                    if (c != null) {
                        c.close();
                    }
                }
                if(callbackContext != null) {
                    callbackContext.success(stepResponse);
                }

            });
        } catch (Exception e) {
            JSONArray stepResponse = new JSONArray();
            Log.e("StepCounterReader", "Getting daily step trend fails.", e);
            callbackContext.success(stepResponse);
        }
    }

    private void readStepCountBinning(final long startTime, String deviceUuid) {

        Filter filter = Filter.eq(HealthConstants.StepCount.DEVICE_UUID, deviceUuid);

        // Get 10 minute binning data of a particular device
        AggregateRequest request = new AggregateRequest.Builder()
                .setDataType(HealthConstants.StepCount.HEALTH_DATA_TYPE)
                .addFunction(AggregateFunction.SUM, HealthConstants.StepCount.COUNT, ALIAS_TOTAL_COUNT)
                .setTimeGroup(TimeGroupUnit.MINUTELY, 10, HealthConstants.StepCount.START_TIME,
                        HealthConstants.StepCount.TIME_OFFSET, ALIAS_BINNING_TIME)
                .setLocalTimeRange(HealthConstants.StepCount.START_TIME, HealthConstants.StepCount.TIME_OFFSET,
                        startTime, startTime + ONE_DAY)
                .setFilter(filter)
                .setSort(ALIAS_BINNING_TIME, SortOrder.ASC)
                .build();

        try {
            mResolver.aggregate(request).setResultListener(result -> {

                List<StepBinningData> binningCountArray = new ArrayList<>();

                try {
                    for (HealthData data : result) {
                        String binningTime = data.getString(ALIAS_BINNING_TIME);
                        int binningCount = data.getInt(ALIAS_TOTAL_COUNT);

                        if (binningTime !=null) {
                            binningCountArray.add(new StepBinningData(binningTime.split(" ")[1], binningCount));
                        }
                    }



                } finally {
                    result.close();
                }
            });
        } catch (Exception e) {
            Log.e("StepDiary", "Getting step binning data fails.", e);
        }
    }

    private static List<StepBinningData> getBinningData(byte[] zip) {
        // decompress ZIP
        List<StepBinningData> binningDataList = HealthDataUtil.getStructuredDataList(zip, StepBinningData.class);
        for (int i = binningDataList.size() - 1; i >= 0; i--) {
            if (binningDataList.get(i).count == 0) {
                binningDataList.remove(i);
            } else {
                binningDataList.get(i).time = String.format(Locale.US, "%02d:%02d", i / 6, (i % 6) * 10);
            }
        }

        return binningDataList;
    }

    public static class StepBinningData {
        public String time;
        public final int count;

        public StepBinningData(String time, int count) {
            this.time = time;
            this.count = count;
        }
    }
}
