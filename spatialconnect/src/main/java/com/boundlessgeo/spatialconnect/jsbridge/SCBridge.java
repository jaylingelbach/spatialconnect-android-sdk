/**
 * Copyright 2016 Boundless, http://boundlessgeo.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License
 */
package com.boundlessgeo.spatialconnect.jsbridge;

import android.location.Location;
import android.support.annotation.Nullable;
import android.util.Log;

import com.boundlessgeo.spatialconnect.SpatialConnect;
import com.boundlessgeo.spatialconnect.config.SCFormConfig;
import com.boundlessgeo.spatialconnect.config.SCFormField;
import com.boundlessgeo.spatialconnect.geometries.SCBoundingBox;
import com.boundlessgeo.spatialconnect.geometries.SCGeometry;
import com.boundlessgeo.spatialconnect.geometries.SCGeometryFactory;
import com.boundlessgeo.spatialconnect.geometries.SCSpatialFeature;
import com.boundlessgeo.spatialconnect.query.SCGeometryPredicateComparison;
import com.boundlessgeo.spatialconnect.query.SCPredicate;
import com.boundlessgeo.spatialconnect.query.SCQueryFilter;
import com.boundlessgeo.spatialconnect.services.SCSensorService;
import com.boundlessgeo.spatialconnect.stores.DefaultStore;
import com.boundlessgeo.spatialconnect.stores.SCDataStore;
import com.boundlessgeo.spatialconnect.stores.SCKeyTuple;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.List;

import rx.Subscriber;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * This module handles messages sent from Javascript.
 */
public class SCBridge extends ReactContextBaseJavaModule {

    private static final String LOG_TAG = SCBridge.class.getSimpleName();
    private final SpatialConnect sc;
    private final ReactContext reactContext;

    public SCBridge(ReactApplicationContext reactContext) {
        super(reactContext);
        this.sc = SpatialConnect.getInstance();
        this.sc.initialize(reactContext.getApplicationContext());
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "SCBridge";
    }

    /**
     * Sends an event to Javascript.
     *
     * @param eventName the name of the event
     * @param params    a map of the key/value pairs associated with the event
     * @see <a href="https://facebook.github.io/react-native/docs/native-modules-android
     * .html#sending-events-to-javascript"> https://facebook.github.io/react-native/docs/native-modules-android
     * .html#sending-events-to-javascript</a>
     */
    public void sendEvent(String eventName, @Nullable WritableMap params) {
        Log.v(LOG_TAG, String.format("Sending event %s to Javascript: %s", eventName, params.toString()));
        this.reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    /**
     * Sends an event to Javascript.
     *
     * @param eventName     the name of the event
     * @param payloadString a payload string associated with the event
     * @see <a href="https://facebook.github.io/react-native/docs/native-modules-android
     * .html#sending-events-to-javascript"> https://facebook.github.io/react-native/docs/native-modules-android
     * .html#sending-events-to-javascript</a>
     */
    public void sendEvent(String eventName, @Nullable String payloadString) {
        Log.v(LOG_TAG, String.format("Sending event %s to Javascript: %s", eventName, payloadString));
        this.reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, payloadString);
    }

    /**
     * Handles a message sent from Javascript.  Expects the message envelope to look like:
     * <code>{"action":<integer>,"payload":<JSON Object>}</code>
     *
     * @param message
     */
    @ReactMethod
    public void handler(ReadableMap message) {
        Log.d(LOG_TAG, "Received message from JS: " + message.toString());
        message = message.getMap("data");

        if (message == null && message.equals("undefined")) {
            Log.w(LOG_TAG, "data message was null or undefined");
            return;
        }
        else {
            // parse bridge message to determine command
            Integer actionNumber = message.getInt("action");
            BridgeCommand command = BridgeCommand.fromActionNumber(actionNumber);
            if (command.equals(BridgeCommand.START_ALL_SERVICES)) {
                handleStartAllServices();
            }
            if (command.equals(BridgeCommand.SENSORSERVICE_GPS)) {
                handleSensorServiceGps(message);
            }
            if (command.equals(BridgeCommand.DATASERVICE_ACTIVESTORESLIST)) {
                handleActiveStoresList();
            }
            if (command.equals(BridgeCommand.DATASERVICE_ACTIVESTOREBYID)) {
                handleActiveStoreById(message);
            }
            if (command.equals(BridgeCommand.DATASERVICE_GEOSPATIALQUERYALL)
                    || command.equals(BridgeCommand.DATASERVICE_SPATIALQUERYALL)) {
                handleQueryAll(message);
            }
            if (command.equals(BridgeCommand.DATASERVICE_UPDATEFEATURE)) {
                handleUpdateFeature(message);
            }
            if (command.equals(BridgeCommand.DATASERVICE_DELETEFEATURE)) {
                handleDeleteFeature(message);
            }
            if (command.equals(BridgeCommand.DATASERVICE_CREATEFEATURE)) {
                handleCreateFeature(message);
            }
            if (command.equals(BridgeCommand.DATASERVICE_FORMSLIST)) {
                handleFormsList();
            }
        }
    }


    /**
     * Handles the {@link BridgeCommand#START_ALL_SERVICES} command.
     */
    private void handleStartAllServices() {
        Log.d(LOG_TAG, "Handling START_ALL_SERVICES message");
        sc.startAllServices();
    }

    /**
     * Handles all the {@link BridgeCommand#SENSORSERVICE_GPS} commands.
     *
     * @param message
     */
    private void handleSensorServiceGps(ReadableMap message) {
        Log.d(LOG_TAG, "Handling SENSORSERVICE_GPS message :" + message.toString());
        SCSensorService sensorService = sc.getSensorService();
        Integer payloadNumber = message.getInt("payload");
        if (payloadNumber == 1) {
            sensorService.startGPSListener();
            sensorService.getLastKnownLocation()
                    .subscribeOn(Schedulers.newThread())
                    .subscribe(new Action1<Location>() {
                        @Override
                        public void call(Location location) {
                            WritableMap params = Arguments.createMap();
                            params.putString("latitude", String.valueOf(location.getLatitude()));
                            params.putString("longitude", String.valueOf(location.getLongitude()));
                            sendEvent("lastKnownLocation", params);
                        }
                    });
        }
        if (payloadNumber == 0) {
            sensorService.disableGPSListener();
        }
    }

    /**
     * Handles the {@link BridgeCommand#DATASERVICE_ACTIVESTORESLIST} command.
     */
    private void handleActiveStoresList() {
        Log.d(LOG_TAG, "Handling DATASERVICE_ACTIVESTORESLIST message");
        List<SCDataStore> stores = sc.getDataService().getActiveStores();
        WritableMap eventPayload = Arguments.createMap();
        WritableArray storesArray = Arguments.createArray();
        for (SCDataStore store : stores) {
            storesArray.pushMap(getStoreMap(store));
        }
        eventPayload.putArray("stores", storesArray);
        sendEvent("storesList", eventPayload);
    }

    /**
     * Handles the {@link BridgeCommand#DATASERVICE_FORMSLIST} command.
     */
    private void handleFormsList() {
        Log.d(LOG_TAG, "Handling DATASERVICE_FORMSLIST message");
        List<SCFormConfig> formConfigs = sc.getDataService().getDefaultStore().getFormConfigs();
        WritableMap eventPayload = Arguments.createMap();
        WritableArray formsArray = Arguments.createArray();
        for (SCFormConfig config : formConfigs) {
            formsArray.pushMap(getFormMap(config));
        }
        eventPayload.putArray("forms", formsArray);
        sendEvent("formsList", eventPayload);
    }

    /**
     * Handles all the {@link BridgeCommand#DATASERVICE_ACTIVESTOREBYID} commands.
     *
     * @param message
     */
    private void handleActiveStoreById(ReadableMap message) {
        Log.d(LOG_TAG, "Handling ACTIVESTOREBYID message :" + message.toString());
        String storeId = message.getMap("payload").getString("storeId");
        SCDataStore store = sc.getDataService().getStoreById(storeId);
        sendEvent("store", getStoreMap(store));
    }

    /**
     * Handles the {@link BridgeCommand#DATASERVICE_GEOSPATIALQUERYALL} and
     * {@link BridgeCommand#DATASERVICE_SPATIALQUERYALL} commands.
     *
     * @param message
     */
    private void handleQueryAll(ReadableMap message) {
        Log.d(LOG_TAG, "Handling *QUERYALL message :" + message.toString());
        SCQueryFilter filter = getFilter(message);
        if (filter != null) {
            sc.getDataService().queryAllStores(filter)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            new Subscriber<SCSpatialFeature>() {
                                @Override
                                public void onCompleted() {
                                    Log.d(LOG_TAG, "query observable completed");
                                }

                                @Override
                                public void onError(Throwable e) {
                                    Log.e(LOG_TAG, "onError()\n" + e.getMessage());
                                }

                                @Override
                                public void onNext(SCSpatialFeature feature) {
                                    try {
                                        // base64 encode id and set it before sending across wire
                                        String encodedId = ((SCGeometry) feature).getKey().encodedCompositeKey();
                                        feature.setId(encodedId);
                                        sendEvent("spatialQuery", ((SCGeometry) feature).toJson());
                                    }
                                    catch (UnsupportedEncodingException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                    );
        }
    }

    /**
     * Handles the {@link BridgeCommand#DATASERVICE_UPDATEFEATURE} command.
     *
     * @param message
     */
    private void handleUpdateFeature(ReadableMap message) {
        Log.d(LOG_TAG, "Handling UPDATEFEATURE message :" + message.toString());
        try {
            SCSpatialFeature featureToUpdate = getFeatureToUpdate(message.getMap("payload").getString("feature"));
            sc.getDataService().getStoreById(featureToUpdate.getKey().getStoreId())
                    .update(featureToUpdate)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            new Subscriber<SCSpatialFeature>() {
                                @Override
                                public void onCompleted() {
                                    Log.d(LOG_TAG, "update completed");
                                }

                                @Override
                                public void onError(Throwable e) {
                                    Log.e(LOG_TAG, "onError()\n" + e.getLocalizedMessage());
                                }

                                @Override
                                public void onNext(SCSpatialFeature updated) {
                                    Log.d(LOG_TAG, "feature updated!");
                                    //TODO: send this over some "update" stream
                                }
                            }
                    );
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles the {@link BridgeCommand#DATASERVICE_DELETEFEATURE} command.
     *
     * @param message
     */
    private void handleDeleteFeature(ReadableMap message) {
        Log.d(LOG_TAG, "Handling DELETEFEATURE message :" + message.toString());
        try {
            SCKeyTuple featureKey = new SCKeyTuple(message.getString("payload"));
            sc.getDataService().getStoreById(featureKey.getStoreId())
                    .delete(featureKey)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            new Subscriber<Boolean>() {
                                @Override
                                public void onCompleted() {
                                    Log.d(LOG_TAG, "delete completed");
                                }

                                @Override
                                public void onError(Throwable e) {
                                    Log.e(LOG_TAG, "onError()\n" + e.getLocalizedMessage());
                                }

                                @Override
                                public void onNext(Boolean deleted) {
                                    //TODO: send this over some "deleted" stream
                                    Log.d(LOG_TAG, "feature deleted!");
                                }
                            }
                    );
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }


    /**
     * Handles the {@link BridgeCommand#DATASERVICE_CREATEFEATURE} command.
     *
     * @param message
     */
    private void handleCreateFeature(ReadableMap message) {
        Log.d(LOG_TAG, "Handling CREATEFEATURE message :" + message.toString());
        try {
            SCSpatialFeature newFeature = getNewFeature(message.getMap("payload"));
            // if no store was specified, use the default store
            if(newFeature.getKey().getStoreId() == null || newFeature.getKey().getStoreId().isEmpty()) {
                newFeature.setStoreId(DefaultStore.NAME);
            }
            sc.getDataService().getStoreById(newFeature.getKey().getStoreId())
                    .create(newFeature)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            new Subscriber<SCSpatialFeature>() {
                                @Override
                                public void onCompleted() {
                                    Log.d(LOG_TAG, "create completed");
                                }

                                @Override
                                public void onError(Throwable e) {
                                    e.printStackTrace();
                                    Log.e(LOG_TAG, "onError()\n" + e.getLocalizedMessage());
                                }

                                @Override
                                public void onNext(SCSpatialFeature feature) {
                                    try {
                                        // base64 encode id and set it before sending across wire
                                        String encodedId = feature.getKey().encodedCompositeKey();
                                        feature.setId(encodedId);
                                        sendEvent("createFeature", feature.toJson());
                                    } catch (UnsupportedEncodingException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                    );
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns an SCSpatialFeature instance based on the message from the bridge to create a new feature.
     *
     * @param message the message received from the Javascript
     * @return
     * @throws UnsupportedEncodingException
     */
    private SCSpatialFeature getNewFeature(ReadableMap message) throws UnsupportedEncodingException {
        String featureString = convertMapToJson(message.getMap("feature")).toString();
        Log.d(LOG_TAG, "new feature: " + featureString);
        return new SCGeometryFactory().getSpatialFeatureFromFeatureJson(featureString);
    }

    /**
     * Returns an SCSpatialFeature instance based on the GeoJSON Feature string sent from the bridge for update.
     *
     * @param featureString the GeoJSON string representing the feature
     * @return
     * @throws UnsupportedEncodingException
     */
    private SCSpatialFeature getFeatureToUpdate(String featureString) throws UnsupportedEncodingException {
        SCSpatialFeature feature = new SCGeometryFactory().getSpatialFeatureFromFeatureJson(featureString);
        SCKeyTuple decodedTuple = new SCKeyTuple(feature.getId());
        // update feature with decoded values
        feature.setStoreId(decodedTuple.getStoreId());
        feature.setLayerId(decodedTuple.getLayerId());
        feature.setId(decodedTuple.getFeatureId());
        return feature;
    }

    // builds a query filter based on the filter in payload
    private SCQueryFilter getFilter(ReadableMap message) {
        ReadableArray extent = message.getMap("payload").getMap("filter").getArray("$geocontains");
        SCBoundingBox bbox = new SCBoundingBox(
                extent.getDouble(0),
                extent.getDouble(1),
                extent.getDouble(2),
                extent.getDouble(3)
        );
        SCQueryFilter filter = new SCQueryFilter(
                new SCPredicate(bbox, SCGeometryPredicateComparison.SCPREDICATE_OPERATOR_WITHIN)
        );
        return filter;
    }

    // creates a WriteableMap of the SCDataStore attributes
    private WritableMap getStoreMap(SCDataStore store) {
        WritableMap params = Arguments.createMap();
        params.putString("storeId", store.getStoreId());
        params.putString("name", store.getName());
        params.putString("type", store.getType());
        params.putInt("version", store.getVersion());
        params.putString("key", store.getKey());
        return params;
    }

    // creates a WriteableMap of the SCFormConfig attributes
    private WritableMap getFormMap(SCFormConfig formConfig) {
        WritableMap params = Arguments.createMap();
        params.putString("id", formConfig.getId());
        params.putString("name", formConfig.getName());
        params.putString("display_name", formConfig.getDisplayName());
        params.putString("layer_name", formConfig.getLayerName());
        WritableArray fields = Arguments.createArray();
        // for each field, create a WriteableMap with all the SCFormField params
        for (SCFormField field: formConfig.getFields()) {
            WritableMap fieldMap = Arguments.createMap();
            fieldMap.putString("id", field.getId());
            fieldMap.putString("key", field.getKey());
            fieldMap.putString("label", field.getLabel());
            if (field.isRequired() != null) {
                fieldMap.putBoolean("is_required", field.isRequired());
            }
            if (field.getPosition() != null) {
                fieldMap.putInt("order", field.getPosition());
            }
            if (field.getType() != null) {
                fieldMap.putString("type", field.getType());
            }
            if (field.getInitialValue() != null) {
                fieldMap.putString("initial_value", String.valueOf(field.getInitialValue()));
            }
            if (field.getMaximum() != null) {
                fieldMap.putDouble("minimum", Double.valueOf(String.valueOf(field.getMinimum())));
            }
            if (field.getMaximum() != null) {
                fieldMap.putDouble("maximum", Double.valueOf(String.valueOf(field.getMaximum())));
            }
            if (field.isExclusiveMaximum() != null) {
                fieldMap.putBoolean("exclusive_maximum", field.isExclusiveMaximum());
            }
            if (field.isExclusiveMinimum() != null) {
                fieldMap.putBoolean("exclusive_minimum", field.isExclusiveMinimum());
            }
            if (field.isInteger() != null) {
                fieldMap.putBoolean("is_integer", field.isInteger());
            }
            if (field.getMaximumLength() != null) {
                fieldMap.putInt("maximum_length", field.getMaximumLength());
            }
            if (field.getMinimumLength() != null) {
                fieldMap.putInt("minimum_length", field.getMinimumLength());
            }
            if (field.getPattern() != null) {
                fieldMap.putString("pattern", field.getPattern());
            }
            fields.pushMap(fieldMap);
        }
        params.putArray("fields", fields);
        return params;
    }

    private static WritableMap convertJsonToMap(JSONObject jsonObject) throws JSONException {
        WritableMap map = new WritableNativeMap();

        Iterator<String> iterator = jsonObject.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            Object value = jsonObject.get(key);
            if (value instanceof JSONObject) {
                map.putMap(key, convertJsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                map.putArray(key, convertJsonToArray((JSONArray) value));
            } else if (value instanceof  Boolean) {
                map.putBoolean(key, (Boolean) value);
            } else if (value instanceof  Integer) {
                map.putInt(key, (Integer) value);
            } else if (value instanceof  Double) {
                map.putDouble(key, (Double) value);
            } else if (value instanceof String)  {
                map.putString(key, (String) value);
            } else {
                map.putString(key, value.toString());
            }
        }
        return map;
    }

    private static WritableArray convertJsonToArray(JSONArray jsonArray) throws JSONException {
        WritableArray array = new WritableNativeArray();

        for (int i = 0; i < jsonArray.length(); i++) {
            Object value = jsonArray.get(i);
            if (value instanceof JSONObject) {
                array.pushMap(convertJsonToMap((JSONObject) value));
            } else if (value instanceof  JSONArray) {
                array.pushArray(convertJsonToArray((JSONArray) value));
            } else if (value instanceof  Boolean) {
                array.pushBoolean((Boolean) value);
            } else if (value instanceof  Integer) {
                array.pushInt((Integer) value);
            } else if (value instanceof  Double) {
                array.pushDouble((Double) value);
            } else if (value instanceof String)  {
                array.pushString((String) value);
            } else {
                array.pushString(value.toString());
            }
        }
        return array;
    }

    private static JSONObject convertMapToJson(ReadableMap readableMap) {
        JSONObject object = new JSONObject();
        ReadableMapKeySetIterator iterator = readableMap.keySetIterator();
        try {

            while (iterator.hasNextKey()) {
                String key = iterator.nextKey();
                switch (readableMap.getType(key)) {
                    case Null:
                        object.put(key, JSONObject.NULL);
                        break;
                    case Boolean:
                        object.put(key, readableMap.getBoolean(key));
                        break;
                    case Number:
                        object.put(key, readableMap.getDouble(key));
                        break;
                    case String:
                        object.put(key, readableMap.getString(key));
                        break;
                    case Map:
                        object.put(key, convertMapToJson(readableMap.getMap(key)));
                        break;
                    case Array:
                        object.put(key, convertArrayToJson(readableMap.getArray(key)));
                        break;
                }
            }
        }
        catch (JSONException e) {
            Log.e(LOG_TAG, "Could not convert to json");
            e.printStackTrace();
        }
        return object;
    }

    private static JSONArray convertArrayToJson(ReadableArray readableArray) throws JSONException {
        JSONArray array = new JSONArray();
        for (int i = 0; i < readableArray.size(); i++) {
            switch (readableArray.getType(i)) {
                case Null:
                    break;
                case Boolean:
                    array.put(readableArray.getBoolean(i));
                    break;
                case Number:
                    array.put(readableArray.getDouble(i));
                    break;
                case String:
                    array.put(readableArray.getString(i));
                    break;
                case Map:
                    array.put(convertMapToJson(readableArray.getMap(i)));
                    break;
                case Array:
                    array.put(convertArrayToJson(readableArray.getArray(i)));
                    break;
            }
        }
        return array;
    }
}
