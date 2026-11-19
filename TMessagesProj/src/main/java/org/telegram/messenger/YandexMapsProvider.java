package org.telegram.messenger;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;

import com.yandex.mapkit.Animation;
import com.yandex.mapkit.MapKitFactory;
import com.yandex.mapkit.ScreenPoint;
import com.yandex.mapkit.ScreenRect;
import com.yandex.mapkit.geometry.BoundingBox;
import com.yandex.mapkit.geometry.Circle;
import com.yandex.mapkit.geometry.Geometry;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.location.LocationListener;
import com.yandex.mapkit.location.LocationManager;
import com.yandex.mapkit.location.LocationStatus;
import com.yandex.mapkit.location.Purpose;
import com.yandex.mapkit.location.SubscriptionSettings;
import com.yandex.mapkit.location.UseInBackground;
import com.yandex.mapkit.map.CameraListener;
import com.yandex.mapkit.map.CameraUpdateReason;
import com.yandex.mapkit.map.CircleMapObject;
import com.yandex.mapkit.map.IconStyle;
import com.yandex.mapkit.map.Map;
import com.yandex.mapkit.map.MapLoadedListener;
import com.yandex.mapkit.map.MapObject;
import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.MapObjectTapListener;
import com.yandex.mapkit.map.MapType;
import com.yandex.mapkit.map.MapWindow;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.map.RotationType;
import com.yandex.mapkit.map.SizeChangedListener;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.mapkit.user_location.UserLocationLayer;
import com.yandex.runtime.image.ImageProvider;

import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LocationActivity;
import org.telegram.ui.MirrlyGramSettingsActivity;

import java.util.HashMap;

/**
 * Yandex MapKit implementation of Telegram's {@link IMapsProvider} abstraction.
 */
public class YandexMapsProvider implements IMapsProvider {

    private static volatile boolean mapKitInitialized = false;
    private static volatile String initializedApiKey = "";

    public static boolean isMapKitInitialized() {
        return mapKitInitialized;
    }

    public static String getInitializedApiKey() {
        return initializedApiKey;
    }

    public static boolean isSupportedOnDevice() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    @Override
    public void initializeMaps(Context context) {
        if (!isSupportedOnDevice() || context == null) {
            return;
        }
        String key = MirrlyConfig.getEffectiveYandexMapKitApiKey();
        if (TextUtils.isEmpty(key)) {
            return;
        }
        synchronized (YandexMapsProvider.class) {
            if (mapKitInitialized) {
                return;
            }
            try {
                MapKitFactory.setApiKey(key);
                MapKitFactory.initialize(context.getApplicationContext());
                initializedApiKey = key;
                mapKitInitialized = true;
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    @Override
    public IMapView onCreateMapView(Context context) {
        initializeMaps(context);
        return new YandexMapView(context);
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLng(LatLng latLng) {
        return new YandexCameraUpdate(latLng, Float.NaN, null, 0);
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngZoom(LatLng latLng, float zoom) {
        return new YandexCameraUpdate(latLng, zoom, null, 0);
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngBounds(ILatLngBounds bounds, int padding) {
        return new YandexCameraUpdate(null, Float.NaN, (YandexLatLngBounds) bounds, padding);
    }

    @Override
    public ILatLngBoundsBuilder onCreateLatLngBoundsBuilder() {
        return new YandexLatLngBoundsBuilder();
    }

    @Override
    public IMapStyleOptions loadRawResourceStyle(Context context, int resId) {
        return new YandexMapStyleOptions(resId != 0);
    }

    @Override
    public String getMapsAppPackageName() {
        return null;
    }

    @Override
    public int getInstallMapsString() {
        return R.string.InstallYandexMaps;
    }

    @Override
    public IMarkerOptions onCreateMarkerOptions() {
        return new YandexMarkerOptions();
    }

    @Override
    public ICircleOptions onCreateCircleOptions() {
        return new YandexCircleOptions();
    }

    private static ImageProvider imageProviderFromRes(Context context, int resId) {
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resId);
        if (bitmap != null) {
            return ImageProvider.fromBitmap(bitmap);
        }
        Drawable drawable = ContextCompat.getDrawable(context, resId);
        if (drawable == null) {
            Bitmap empty = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            return ImageProvider.fromBitmap(empty);
        }
        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());
        Bitmap rendered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(rendered);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        return ImageProvider.fromBitmap(rendered);
    }

    public static final class YandexMapView implements IMapView {
        private final FrameLayout container;
        private MapView mapView;
        private YandexMapImpl mapImpl;
        private boolean started = false;
        private boolean destroyed = false;

        private ITouchInterceptor dispatchInterceptor;
        private ITouchInterceptor interceptInterceptor;
        private Runnable onLayoutListener;

        private YandexMapView(Context context) {
            container = new FrameLayout(context) {
                @Override
                public boolean dispatchTouchEvent(MotionEvent ev) {
                    if (dispatchInterceptor != null) {
                        return dispatchInterceptor.onInterceptTouchEvent(ev, super::dispatchTouchEvent);
                    }
                    return super.dispatchTouchEvent(ev);
                }

                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    if (interceptInterceptor != null) {
                        return interceptInterceptor.onInterceptTouchEvent(ev, super::onInterceptTouchEvent);
                    }
                    return super.onInterceptTouchEvent(ev);
                }

                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    super.onLayout(changed, left, top, right, bottom);
                    if (onLayoutListener != null) {
                        onLayoutListener.run();
                    }
                }
            };

            if (isSupportedOnDevice() && mapKitInitialized) {
                try {
                    View inflated = LayoutInflater.from(context).inflate(R.layout.yandex_map_view, container, false);
                    if (inflated instanceof MapView) {
                        mapView = (MapView) inflated;
                    } else {
                        mapView = new MapView(context);
                    }
                    container.addView(mapView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                    mapImpl = new YandexMapImpl(context, mapView);
                } catch (Throwable e) {
                    FileLog.e(e);
                    mapView = null;
                    mapImpl = null;
                }
            }

            if (mapView == null) {
                buildMissingKeyPlaceholder(context);
            }
        }

        private void buildMissingKeyPlaceholder(Context context) {
            container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

            LinearLayout box = new LinearLayout(context);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setPadding(dp(24), dp(24), dp(24), dp(24));

            ImageView icon = new ImageView(context);
            icon.setImageResource(R.drawable.msg_location);
            icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon));
            box.addView(icon, LayoutHelper.createLinear(48, 48, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

            TextView title = new TextView(context);
            title.setText(LocaleController.getString(R.string.MirrlyYandexKeyMissingTitle));
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTypeface(AndroidUtilities.bold());
            title.setGravity(Gravity.CENTER);
            box.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

            TextView subtitle = new TextView(context);
            subtitle.setText(LocaleController.getString(R.string.MirrlyYandexKeyMissingInfo));
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setGravity(Gravity.CENTER);
            box.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

            TextView button = new TextView(context);
            button.setText(LocaleController.getString(R.string.MirrlyOpenSettings));
            button.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            button.setTypeface(AndroidUtilities.bold());
            button.setPadding(dp(18), dp(9), dp(18), dp(9));
            button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                    dp(8),
                    Theme.getColor(Theme.key_windowBackgroundWhite),
                    Theme.getColor(Theme.key_listSelector)
            ));
            button.setOnClickListener(v -> {
                BaseFragment lastFragment = LaunchActivity.getLastFragment();
                if (lastFragment == null) {
                    return;
                }
                lastFragment.dismissCurrentDialog();
                boolean removeLast = lastFragment instanceof LocationActivity;
                lastFragment.presentFragment(new MirrlyGramSettingsActivity(), removeLast);
            });
            box.addView(button, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            container.addView(box, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }

        @Override
        public View getView() {
            return container;
        }

        @Override
        public void getMapAsync(Consumer<IMap> callback) {
            if (callback == null) {
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (destroyed) {
                    return;
                }
                if (mapImpl != null) {
                    callback.accept(mapImpl);
                } else {
                    callback.accept(new PlaceholderMapImpl());
                }
            });
        }

        @Override
        public void onResume() {
            if (destroyed || mapView == null || started) {
                return;
            }
            started = true;
            try {
                MapKitFactory.getInstance().onStart();
                mapView.onStart();
                if (mapImpl != null) {
                    mapImpl.onResume();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        @Override
        public void onPause() {
            if (mapView == null || !started) {
                return;
            }
            started = false;
            try {
                if (mapImpl != null) {
                    mapImpl.onPause();
                }
                mapView.onStop();
                MapKitFactory.getInstance().onStop();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        @Override
        public void onCreate(Bundle savedInstance) {
            // MapKit MapView initializes on UI creation and must not run on LocationActivity's preload thread.
        }

        @Override
        public void onDestroy() {
            if (destroyed) {
                return;
            }
            destroyed = true;
            onPause();
            if (mapImpl != null) {
                mapImpl.onDestroy();
            }
        }

        @Override
        public void onLowMemory() {
        }

        @Override
        public void setOnDispatchTouchEventInterceptor(ITouchInterceptor touchInterceptor) {
            dispatchInterceptor = touchInterceptor;
        }

        @Override
        public void setOnInterceptTouchEventInterceptor(ITouchInterceptor touchInterceptor) {
            interceptInterceptor = touchInterceptor;
        }

        @Override
        public void setOnLayoutListener(Runnable callback) {
            onLayoutListener = callback;
        }
    }

    public static final class YandexMapImpl implements IMap {
        private final Context context;
        private final MapWindow mapWindow;
        private final Map yandexMap;
        private final MapObjectCollection objectsCollection;

        private final java.util.Map<MapObject, YandexMarker> implToAbsMarkerMap = new HashMap<>();
        private final java.util.Map<MapObject, YandexCircle> implToAbsCircleMap = new HashMap<>();

        private UserLocationLayer userLocationLayer;
        private LocationManager locationManager;
        private boolean myLocationEnabled = false;
        private boolean locationSubscribed = false;

        private int padLeft;
        private int padTop;
        private int padRight;
        private int padBottom;

        private boolean cameraMoving = false;
        private OnCameraMoveStartedListener onCameraMoveStartedListener;
        private Runnable onCameraMoveListener;
        private Runnable onCameraIdleListener;
        private Runnable onMapLoadedCallback;
        private Consumer<Location> onMyLocationChangeCallback;
        private OnMarkerClickListener onMarkerClickListener;

        // Strong references are required because MapKit stores listener references weakly.
        private final CameraListener cameraListener = new CameraListener() {
            @Override
            public void onCameraPositionChanged(
                    @NonNull Map map,
                    @NonNull com.yandex.mapkit.map.CameraPosition cameraPosition,
                    @NonNull CameraUpdateReason cameraUpdateReason,
                    boolean finished
            ) {
                if (!cameraMoving) {
                    cameraMoving = true;
                    if (onCameraMoveStartedListener != null) {
                        int reason = cameraUpdateReason == CameraUpdateReason.GESTURES
                                ? OnCameraMoveStartedListener.REASON_GESTURE
                                : OnCameraMoveStartedListener.REASON_API_ANIMATION;
                        onCameraMoveStartedListener.onCameraMoveStarted(reason);
                    }
                }
                if (onCameraMoveListener != null) {
                    onCameraMoveListener.run();
                }
                if (finished) {
                    cameraMoving = false;
                    if (onCameraIdleListener != null) {
                        onCameraIdleListener.run();
                    }
                }
            }
        };

        private final MapLoadedListener mapLoadedListener = statistics -> {
            if (onMapLoadedCallback != null) {
                onMapLoadedCallback.run();
            }
        };

        private final MapObjectTapListener mapObjectTapListener = (mapObject, point) -> {
            if (onMarkerClickListener == null) {
                return false;
            }
            YandexMarker marker = implToAbsMarkerMap.get(mapObject);
            if (marker != null) {
                return onMarkerClickListener.onClick(marker);
            }
            return false;
        };

        private final SizeChangedListener sizeChangedListener = (window, newWidth, newHeight) -> applyFocusRect();

        private final LocationListener yandexLocationListener = new LocationListener() {
            @Override
            public void onLocationUpdated(@NonNull com.yandex.mapkit.location.Location yLocation) {
                if (onMyLocationChangeCallback == null || yLocation.getPosition() == null) {
                    return;
                }
                Location out = new Location("yandex");
                out.setLatitude(yLocation.getPosition().getLatitude());
                out.setLongitude(yLocation.getPosition().getLongitude());
                if (yLocation.getAccuracy() != null) {
                    out.setAccuracy(yLocation.getAccuracy().floatValue());
                }
                if (yLocation.getAltitude() != null) {
                    out.setAltitude(yLocation.getAltitude());
                }
                if (yLocation.getHeading() != null) {
                    out.setBearing(yLocation.getHeading().floatValue());
                }
                if (yLocation.getSpeed() != null) {
                    out.setSpeed(yLocation.getSpeed().floatValue());
                }
                out.setTime(yLocation.getAbsoluteTimestamp());
                AndroidUtilities.runOnUIThread(() -> {
                    if (onMyLocationChangeCallback != null) {
                        onMyLocationChangeCallback.accept(out);
                    }
                });
            }

            @Override
            public void onLocationStatusUpdated(@NonNull LocationStatus locationStatus) {
            }
        };

        private YandexMapImpl(Context context, MapView mapView) {
            this.context = context;
            this.mapWindow = mapView.getMapWindow();
            this.yandexMap = mapWindow.getMap();
            this.objectsCollection = yandexMap.getMapObjects().addCollection();

            yandexMap.addCameraListener(cameraListener);
            yandexMap.setMapLoadedListener(mapLoadedListener);
            objectsCollection.addTapListener(mapObjectTapListener);
            mapWindow.addSizeChangedListener(sizeChangedListener);
        }

        private void onResume() {
            updateLocationSubscription();
        }

        private void onPause() {
            unsubscribeLocation();
        }

        private void onDestroy() {
            unsubscribeLocation();
            onCameraMoveStartedListener = null;
            onCameraMoveListener = null;
            onCameraIdleListener = null;
            onMapLoadedCallback = null;
            onMyLocationChangeCallback = null;
            onMarkerClickListener = null;
            if (yandexMap.isValid()) {
                yandexMap.removeCameraListener(cameraListener);
                yandexMap.setMapLoadedListener(null);
            }
            if (objectsCollection.isValid()) {
                objectsCollection.removeTapListener(mapObjectTapListener);
                objectsCollection.clear();
            }
            if (mapWindow.isValid()) {
                mapWindow.removeSizeChangedListener(sizeChangedListener);
            }
            implToAbsMarkerMap.clear();
            implToAbsCircleMap.clear();
        }

        @Override
        public void setMapType(int mapType) {
            if (!yandexMap.isValid()) {
                return;
            }
            switch (mapType) {
                case MAP_TYPE_SATELLITE:
                    yandexMap.setMapType(MapType.SATELLITE);
                    break;
                case MAP_TYPE_HYBRID:
                    yandexMap.setMapType(MapType.HYBRID);
                    break;
                case MAP_TYPE_NORMAL:
                default:
                    yandexMap.setMapType(MapType.VECTOR_MAP);
                    break;
            }
        }

        @Override
        public float getMaxZoomLevel() {
            if (!yandexMap.isValid()) {
                return 20f;
            }
            return Math.min(21f, Math.max(15f, yandexMap.getCameraBounds().getMaxZoom()));
        }

        @Override
        public float getMinZoomLevel() {
            if (!yandexMap.isValid()) {
                return 2f;
            }
            return Math.max(0f, yandexMap.getCameraBounds().getMinZoom());
        }

        @SuppressLint("MissingPermission")
        @Override
        public void setMyLocationEnabled(boolean enabled) {
            myLocationEnabled = enabled;
            if (!enabled) {
                if (userLocationLayer != null && userLocationLayer.isValid()) {
                    userLocationLayer.setVisible(false);
                }
                unsubscribeLocation();
                return;
            }
            boolean hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (!hasPermission) {
                return;
            }
            if (userLocationLayer == null && mapWindow.isValid()) {
                userLocationLayer = MapKitFactory.getInstance().createUserLocationLayer(mapWindow);
            }
            if (userLocationLayer != null && userLocationLayer.isValid()) {
                userLocationLayer.setVisible(true);
            }
            updateLocationSubscription();
        }

        private void updateLocationSubscription() {
            if (!myLocationEnabled || onMyLocationChangeCallback == null || locationSubscribed) {
                return;
            }
            boolean hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (!hasPermission) {
                return;
            }
            try {
                if (locationManager == null) {
                    locationManager = MapKitFactory.getInstance().createLocationManager();
                }
                locationManager.subscribeForLocationUpdates(
                        new SubscriptionSettings(UseInBackground.DISALLOW, Purpose.GENERAL),
                        yandexLocationListener
                );
                locationSubscribed = true;
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        private void unsubscribeLocation() {
            if (locationManager != null && locationSubscribed) {
                try {
                    locationManager.unsubscribe(yandexLocationListener);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                locationSubscribed = false;
            }
        }

        @Override
        public IUISettings getUiSettings() {
            return new YandexUISettings();
        }

        @Override
        public void setOnCameraIdleListener(Runnable callback) {
            onCameraIdleListener = callback;
        }

        @Override
        public void setOnCameraMoveStartedListener(OnCameraMoveStartedListener listener) {
            onCameraMoveStartedListener = listener;
        }

        @Override
        public CameraPosition getCameraPosition() {
            if (!yandexMap.isValid()) {
                return new CameraPosition(new LatLng(0, 0), 15f);
            }
            com.yandex.mapkit.map.CameraPosition pos = yandexMap.getCameraPosition();
            Point target = pos.getTarget();
            return new CameraPosition(new LatLng(target.getLatitude(), target.getLongitude()), pos.getZoom());
        }

        @Override
        public void setOnMapLoadedCallback(Runnable callback) {
            onMapLoadedCallback = callback;
        }

        @Override
        public IProjection getProjection() {
            return new YandexProjection(mapWindow);
        }

        @Override
        public void setPadding(int left, int top, int right, int bottom) {
            padLeft = left;
            padTop = top;
            padRight = right;
            padBottom = bottom;
            applyFocusRect();
        }

        private void applyFocusRect() {
            if (!mapWindow.isValid()) {
                return;
            }
            int w = mapWindow.width();
            int h = mapWindow.height();
            if (w <= padLeft + padRight + 10 || h <= padTop + padBottom + 10) {
                return;
            }
            ScreenRect focusRect = new ScreenRect(
                    new ScreenPoint(padLeft, padTop),
                    new ScreenPoint(w - padRight, h - padBottom)
            );
            mapWindow.setFocusRect(focusRect);
            mapWindow.setFocusPoint(new ScreenPoint(
                    padLeft + (w - padLeft - padRight) / 2f,
                    padTop + (h - padTop - padBottom) / 2f
            ));
        }

        @Override
        public void setMapStyle(IMapStyleOptions style) {
            if (!yandexMap.isValid()) {
                return;
            }
            boolean night = style instanceof YandexMapStyleOptions && ((YandexMapStyleOptions) style).nightMode;
            yandexMap.setNightModeEnabled(night);
        }

        @Override
        public IMarker addMarker(IMarkerOptions markerOptions) {
            YandexMarkerOptions opts = (YandexMarkerOptions) markerOptions;
            PlacemarkMapObject placemark = objectsCollection.addPlacemark();
            if (opts.position != null) {
                placemark.setGeometry(new Point(opts.position.latitude, opts.position.longitude));
            }
            IconStyle iconStyle = new IconStyle();
            iconStyle.setAnchor(new PointF(opts.anchorX, opts.anchorY));
            iconStyle.setFlat(opts.flat);
            iconStyle.setRotationType(opts.flat ? RotationType.ROTATE : RotationType.NO_ROTATION);
            if (opts.bitmap != null) {
                placemark.setIcon(ImageProvider.fromBitmap(opts.bitmap), iconStyle);
            } else if (opts.iconResId != 0) {
                placemark.setIcon(imageProviderFromRes(context, opts.iconResId), iconStyle);
            } else {
                placemark.setIconStyle(iconStyle);
            }
            YandexMarker abs = new YandexMarker(placemark, iconStyle);
            implToAbsMarkerMap.put(placemark, abs);
            return abs;
        }

        @Override
        public ICircle addCircle(ICircleOptions circleOptions) {
            YandexCircleOptions opts = (YandexCircleOptions) circleOptions;
            Point center = opts.center != null ? new Point(opts.center.latitude, opts.center.longitude) : new Point(0, 0);
            CircleMapObject circle = objectsCollection.addCircle(new Circle(center, (float) opts.radius));
            circle.setStrokeColor(opts.strokeColor);
            circle.setFillColor(opts.fillColor);
            circle.setStrokeWidth(opts.strokeWidth);
            YandexCircle abs = new YandexCircle(circle, center, (float) opts.radius);
            implToAbsCircleMap.put(circle, abs);
            return abs;
        }

        @Override
        public void setOnMyLocationChangeListener(Consumer<Location> callback) {
            onMyLocationChangeCallback = callback;
            if (callback == null) {
                unsubscribeLocation();
            } else {
                updateLocationSubscription();
            }
        }

        @Override
        public void setOnMarkerClickListener(OnMarkerClickListener markerClickListener) {
            onMarkerClickListener = markerClickListener;
        }

        @Override
        public void setOnCameraMoveListener(Runnable callback) {
            onCameraMoveListener = callback;
        }

        @Override
        public void animateCamera(ICameraUpdate update) {
            applyCameraUpdate((YandexCameraUpdate) update, true, 400, null);
        }

        @Override
        public void animateCamera(ICameraUpdate update, ICancelableCallback callback) {
            applyCameraUpdate((YandexCameraUpdate) update, true, 400, callback);
        }

        @Override
        public void animateCamera(ICameraUpdate update, int duration, ICancelableCallback callback) {
            applyCameraUpdate((YandexCameraUpdate) update, true, duration, callback);
        }

        @Override
        public void moveCamera(ICameraUpdate update) {
            applyCameraUpdate((YandexCameraUpdate) update, false, 0, null);
        }

        private void applyCameraUpdate(YandexCameraUpdate update, boolean animate, int durationMs, ICancelableCallback callback) {
            if (update == null || !yandexMap.isValid()) {
                return;
            }
            com.yandex.mapkit.map.CameraPosition targetPos;
            if (update.bounds != null) {
                BoundingBox box = update.bounds.boundingBox;
                double latSpan = Math.abs(box.getNorthEast().getLatitude() - box.getSouthWest().getLatitude());
                double lonSpan = Math.abs(box.getNorthEast().getLongitude() - box.getSouthWest().getLongitude());
                if (latSpan < 1e-5 && lonSpan < 1e-5) {
                    LatLng center = update.bounds.getCenter();
                    targetPos = new com.yandex.mapkit.map.CameraPosition(new Point(center.latitude, center.longitude), 15f, 0f, 0f);
                } else {
                    Geometry geometry = Geometry.fromBoundingBox(box);
                    ScreenRect rect = buildPaddedScreenRect(update.padding);
                    if (rect != null) {
                        targetPos = yandexMap.cameraPosition(geometry, rect);
                    } else {
                        targetPos = yandexMap.cameraPosition(geometry);
                    }
                }
            } else if (update.target != null) {
                float zoom = Float.isNaN(update.zoom) ? yandexMap.getCameraPosition().getZoom() : update.zoom;
                targetPos = new com.yandex.mapkit.map.CameraPosition(
                        new Point(update.target.latitude, update.target.longitude),
                        zoom,
                        0f,
                        0f
                );
            } else {
                return;
            }

            if (!animate) {
                yandexMap.move(targetPos);
                if (callback != null) {
                    callback.onFinish();
                }
                return;
            }
            float durationSec = Math.max(0.05f, durationMs / 1000f);
            Animation anim = new Animation(Animation.Type.SMOOTH, durationSec);
            yandexMap.move(targetPos, anim, callback == null ? null : completed -> {
                if (completed) {
                    callback.onFinish();
                } else {
                    callback.onCancel();
                }
            });
        }

        private ScreenRect buildPaddedScreenRect(int extraPadding) {
            if (!mapWindow.isValid()) {
                return null;
            }
            int w = mapWindow.width();
            int h = mapWindow.height();
            int l = padLeft + extraPadding;
            int t = padTop + extraPadding;
            int r = w - padRight - extraPadding;
            int b = h - padBottom - extraPadding;
            if (w <= 0 || h <= 0 || r <= l + 20 || b <= t + 20) {
                return null;
            }
            return new ScreenRect(new ScreenPoint(l, t), new ScreenPoint(r, b));
        }

        public final class YandexMarker implements IMarker {
            private final PlacemarkMapObject placemark;
            private final IconStyle iconStyle;
            private Object tag;

            private YandexMarker(PlacemarkMapObject placemark, IconStyle iconStyle) {
                this.placemark = placemark;
                this.iconStyle = iconStyle;
            }

            @Override
            public Object getTag() {
                return tag;
            }

            @Override
            public void setTag(Object tag) {
                this.tag = tag;
            }

            @Override
            public LatLng getPosition() {
                if (!placemark.isValid()) {
                    return new LatLng(0, 0);
                }
                Point p = placemark.getGeometry();
                return new LatLng(p.getLatitude(), p.getLongitude());
            }

            @Override
            public void setPosition(LatLng latLng) {
                if (placemark.isValid() && latLng != null) {
                    placemark.setGeometry(new Point(latLng.latitude, latLng.longitude));
                }
            }

            @Override
            public void setRotation(int rotation) {
                if (!placemark.isValid()) {
                    return;
                }
                iconStyle.setRotationType(RotationType.ROTATE);
                placemark.setIconStyle(iconStyle);
                placemark.setDirection((float) rotation);
            }

            @Override
            public void setIcon(Bitmap bitmap) {
                if (placemark.isValid() && bitmap != null) {
                    placemark.setIcon(ImageProvider.fromBitmap(bitmap), iconStyle);
                }
            }

            @Override
            public void setIcon(int resId) {
                if (placemark.isValid() && resId != 0) {
                    placemark.setIcon(imageProviderFromRes(context, resId), iconStyle);
                }
            }

            @Override
            public void remove() {
                if (placemark.isValid() && objectsCollection.isValid()) {
                    objectsCollection.remove(placemark);
                }
                implToAbsMarkerMap.remove(placemark);
            }
        }

        public final class YandexCircle implements ICircle {
            private final CircleMapObject circle;
            private Point center;
            private float radius;

            private YandexCircle(CircleMapObject circle, Point center, float radius) {
                this.circle = circle;
                this.center = center;
                this.radius = radius;
            }

            @Override
            public void setStrokeColor(int color) {
                if (circle.isValid()) {
                    circle.setStrokeColor(color);
                }
            }

            @Override
            public void setFillColor(int color) {
                if (circle.isValid()) {
                    circle.setFillColor(color);
                }
            }

            @Override
            public void setRadius(double radius) {
                this.radius = (float) radius;
                if (circle.isValid()) {
                    circle.setGeometry(new Circle(center, this.radius));
                }
            }

            @Override
            public double getRadius() {
                return radius;
            }

            @Override
            public void setCenter(LatLng latLng) {
                if (latLng == null) {
                    return;
                }
                this.center = new Point(latLng.latitude, latLng.longitude);
                if (circle.isValid()) {
                    circle.setGeometry(new Circle(this.center, radius));
                }
            }

            @Override
            public void remove() {
                if (circle.isValid() && objectsCollection.isValid()) {
                    objectsCollection.remove(circle);
                }
                implToAbsCircleMap.remove(circle);
            }
        }
    }

    public static final class YandexProjection implements IProjection {
        private final MapWindow mapWindow;

        private YandexProjection(MapWindow mapWindow) {
            this.mapWindow = mapWindow;
        }

        @Override
        public android.graphics.Point toScreenLocation(LatLng latLng) {
            if (!mapWindow.isValid() || latLng == null) {
                return new android.graphics.Point(0, 0);
            }
            ScreenPoint sp = mapWindow.worldToScreen(new Point(latLng.latitude, latLng.longitude));
            if (sp == null) {
                return new android.graphics.Point(0, 0);
            }
            return new android.graphics.Point(Math.round(sp.getX()), Math.round(sp.getY()));
        }
    }

    public static final class YandexUISettings implements IUISettings {
        @Override
        public void setZoomControlsEnabled(boolean enabled) {
        }

        @Override
        public void setMyLocationButtonEnabled(boolean enabled) {
        }

        @Override
        public void setCompassEnabled(boolean enabled) {
        }
    }

    public static final class YandexCircleOptions implements ICircleOptions {
        private LatLng center;
        private double radius;
        private int strokeColor;
        private int fillColor;
        private float strokeWidth = 4f;

        @Override
        public ICircleOptions center(LatLng latLng) {
            this.center = latLng;
            return this;
        }

        @Override
        public ICircleOptions radius(double radius) {
            this.radius = radius;
            return this;
        }

        @Override
        public ICircleOptions strokeColor(int color) {
            this.strokeColor = color;
            return this;
        }

        @Override
        public ICircleOptions fillColor(int color) {
            this.fillColor = color;
            return this;
        }

        @Override
        public ICircleOptions strokePattern(java.util.List<PatternItem> patternItems) {
            return this;
        }

        @Override
        public ICircleOptions strokeWidth(int width) {
            this.strokeWidth = Math.max(1f, width);
            return this;
        }
    }

    public static final class YandexMarkerOptions implements IMarkerOptions {
        private LatLng position;
        private Bitmap bitmap;
        private int iconResId;
        private float anchorX = 0.5f;
        private float anchorY = 1.0f;
        private boolean flat = false;

        @Override
        public IMarkerOptions position(LatLng latLng) {
            this.position = latLng;
            return this;
        }

        @Override
        public IMarkerOptions icon(Bitmap bitmap) {
            this.bitmap = bitmap;
            this.iconResId = 0;
            return this;
        }

        @Override
        public IMarkerOptions icon(int resId) {
            this.iconResId = resId;
            this.bitmap = null;
            return this;
        }

        @Override
        public IMarkerOptions anchor(float lat, float lng) {
            this.anchorX = lat;
            this.anchorY = lng;
            return this;
        }

        @Override
        public IMarkerOptions title(String title) {
            return this;
        }

        @Override
        public IMarkerOptions snippet(String snippet) {
            return this;
        }

        @Override
        public IMarkerOptions flat(boolean flat) {
            this.flat = flat;
            return this;
        }
    }

    public static final class YandexLatLngBoundsBuilder implements ILatLngBoundsBuilder {
        private double minLat = Double.POSITIVE_INFINITY;
        private double maxLat = Double.NEGATIVE_INFINITY;
        private double minLon = Double.POSITIVE_INFINITY;
        private double maxLon = Double.NEGATIVE_INFINITY;
        private boolean hasPoints = false;

        @Override
        public ILatLngBoundsBuilder include(LatLng latLng) {
            if (latLng == null) {
                return this;
            }
            hasPoints = true;
            minLat = Math.min(minLat, latLng.latitude);
            maxLat = Math.max(maxLat, latLng.latitude);
            minLon = Math.min(minLon, latLng.longitude);
            maxLon = Math.max(maxLon, latLng.longitude);
            return this;
        }

        @Override
        public ILatLngBounds build() {
            if (!hasPoints) {
                return new YandexLatLngBounds(new BoundingBox(new Point(0, 0), new Point(0, 0)));
            }
            return new YandexLatLngBounds(new BoundingBox(new Point(minLat, minLon), new Point(maxLat, maxLon)));
        }
    }

    public static final class YandexLatLngBounds implements ILatLngBounds {
        private final BoundingBox boundingBox;

        private YandexLatLngBounds(BoundingBox boundingBox) {
            this.boundingBox = boundingBox;
        }

        @Override
        public LatLng getCenter() {
            Point sw = boundingBox.getSouthWest();
            Point ne = boundingBox.getNorthEast();
            return new LatLng(
                    (sw.getLatitude() + ne.getLatitude()) / 2.0,
                    (sw.getLongitude() + ne.getLongitude()) / 2.0
            );
        }
    }

    public static final class YandexCameraUpdate implements ICameraUpdate {
        private final LatLng target;
        private final float zoom;
        private final YandexLatLngBounds bounds;
        private final int padding;

        private YandexCameraUpdate(LatLng target, float zoom, YandexLatLngBounds bounds, int padding) {
            this.target = target;
            this.zoom = zoom;
            this.bounds = bounds;
            this.padding = padding;
        }
    }

    public static final class YandexMapStyleOptions implements IMapStyleOptions {
        private final boolean nightMode;

        private YandexMapStyleOptions(boolean nightMode) {
            this.nightMode = nightMode;
        }
    }

    private static final class PlaceholderMapImpl implements IMap {
        private LatLng center = new LatLng(55.751244, 37.618423);
        private float zoom = 14f;

        @Override
        public void setMapType(int mapType) {
        }

        @Override
        public void animateCamera(ICameraUpdate update) {
            moveCamera(update);
        }

        @Override
        public void animateCamera(ICameraUpdate update, ICancelableCallback callback) {
            moveCamera(update);
            if (callback != null) {
                callback.onFinish();
            }
        }

        @Override
        public void animateCamera(ICameraUpdate update, int duration, ICancelableCallback callback) {
            moveCamera(update);
            if (callback != null) {
                callback.onFinish();
            }
        }

        @Override
        public void moveCamera(ICameraUpdate update) {
            if (update instanceof YandexCameraUpdate) {
                YandexCameraUpdate yUpdate = (YandexCameraUpdate) update;
                if (yUpdate.target != null) {
                    center = yUpdate.target;
                } else if (yUpdate.bounds != null) {
                    center = yUpdate.bounds.getCenter();
                }
                if (!Float.isNaN(yUpdate.zoom)) {
                    zoom = yUpdate.zoom;
                }
            }
        }

        @Override
        public float getMaxZoomLevel() {
            return 20f;
        }

        @Override
        public float getMinZoomLevel() {
            return 2f;
        }

        @Override
        public void setMyLocationEnabled(boolean enabled) {
        }

        @Override
        public IUISettings getUiSettings() {
            return new YandexUISettings();
        }

        @Override
        public void setOnCameraIdleListener(Runnable callback) {
        }

        @Override
        public void setOnCameraMoveStartedListener(OnCameraMoveStartedListener onCameraMoveStartedListener) {
        }

        @Override
        public CameraPosition getCameraPosition() {
            return new CameraPosition(center, zoom);
        }

        @Override
        public void setOnMapLoadedCallback(Runnable callback) {
            if (callback != null) {
                AndroidUtilities.runOnUIThread(callback);
            }
        }

        @Override
        public IProjection getProjection() {
            return latLng -> new android.graphics.Point(0, 0);
        }

        @Override
        public void setPadding(int left, int top, int right, int bottom) {
        }

        @Override
        public void setMapStyle(IMapStyleOptions style) {
        }

        @Override
        public IMarker addMarker(IMarkerOptions markerOptions) {
            YandexMarkerOptions opts = markerOptions instanceof YandexMarkerOptions ? (YandexMarkerOptions) markerOptions : null;
            return new StubMarker(opts != null && opts.position != null ? opts.position : center);
        }

        @Override
        public void setOnMyLocationChangeListener(Consumer<Location> callback) {
        }

        @Override
        public void setOnMarkerClickListener(OnMarkerClickListener markerClickListener) {
        }

        @Override
        public void setOnCameraMoveListener(Runnable callback) {
        }

        @Override
        public ICircle addCircle(ICircleOptions circleOptions) {
            YandexCircleOptions opts = circleOptions instanceof YandexCircleOptions ? (YandexCircleOptions) circleOptions : null;
            return new StubCircle(opts != null ? opts.radius : 0);
        }
    }

    private static final class StubMarker implements IMarker {
        private Object tag;
        private LatLng position;

        private StubMarker(LatLng position) {
            this.position = position;
        }

        @Override
        public Object getTag() {
            return tag;
        }

        @Override
        public void setTag(Object tag) {
            this.tag = tag;
        }

        @Override
        public LatLng getPosition() {
            return position;
        }

        @Override
        public void setPosition(LatLng latLng) {
            if (latLng != null) {
                position = latLng;
            }
        }

        @Override
        public void setRotation(int rotation) {
        }

        @Override
        public void setIcon(Bitmap bitmap) {
        }

        @Override
        public void setIcon(int resId) {
        }

        @Override
        public void remove() {
        }
    }

    private static final class StubCircle implements ICircle {
        private double radius;

        private StubCircle(double radius) {
            this.radius = radius;
        }

        @Override
        public void setStrokeColor(int color) {
        }

        @Override
        public void setFillColor(int color) {
        }

        @Override
        public void setRadius(double radius) {
            this.radius = radius;
        }

        @Override
        public double getRadius() {
            return radius;
        }

        @Override
        public void setCenter(LatLng latLng) {
        }

        @Override
        public void remove() {
        }
    }
}
