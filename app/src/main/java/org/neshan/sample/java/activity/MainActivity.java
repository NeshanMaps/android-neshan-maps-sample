package org.neshan.sample.java.activity;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONObject;
import org.neshan.core.Bounds;
import org.neshan.core.LngLat;
import org.neshan.core.LngLatVector;
import org.neshan.core.Range;
import org.neshan.core.ViewportBounds;
import org.neshan.core.ViewportPosition;
import org.neshan.geometry.LineGeom;
import org.neshan.geometry.PolygonGeom;
import org.neshan.graphics.ARGB;
import org.neshan.layers.VectorElementLayer;
import org.neshan.sample.R;
import org.neshan.sample.java.util.RecordKeeper;
import org.neshan.services.NeshanMapStyle;
import org.neshan.services.NeshanServices;
import org.neshan.styles.AnimationStyle;
import org.neshan.styles.AnimationStyleBuilder;
import org.neshan.styles.AnimationType;
import org.neshan.styles.LineStyle;
import org.neshan.styles.LineStyleCreator;
import org.neshan.styles.MarkerStyle;
import org.neshan.styles.MarkerStyleCreator;
import org.neshan.styles.PolygonStyle;
import org.neshan.styles.PolygonStyleCreator;
import org.neshan.ui.ClickData;
import org.neshan.ui.ClickType;
import org.neshan.ui.MapEventListener;
import org.neshan.ui.MapView;
import org.neshan.utils.BitmapUtils;
import org.neshan.vectorelements.Line;
import org.neshan.vectorelements.Marker;
import org.neshan.vectorelements.Polygon;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = MainActivity.class.getName();

    // used to track request permissions
    final int REQUEST_CODE = 123;

    final int BASE_MAP_INDEX = 0;

    // User's current location
    Location userLocation;
    FusedLocationProviderClient fusedLocationClient;

    LngLat clickedLocation;

    // Neshan Map
    MapView map;
    NeshanMapStyle mapStyle;
    DrawerLayout drawer;
    NavigationView nav;
    Toolbar toolbar;
    Button focusOnUserLocationBtn;

    VectorElementLayer userMarkerLayer;
    VectorElementLayer markerLayer;
    VectorElementLayer lineLayer;
    VectorElementLayer polygonLayer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        initLayoutReferences();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_toolbar, menu);
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        getLocationPermission();
    }

    private void initLayoutReferences() {
        initViews();
        initToolbar();
        initMap();
        initLocation();
        initSideNavigation();

        //on side navigation menu clicked
        nav.setNavigationItemSelectedListener(this);

        // when focusOnUserLocation is clicked, camera points to user location
        focusOnUserLocationBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                getLastLocation();
            }
        });

        // when clicked on map, a marker is added in click location
        map.setMapEventListener(new MapEventListener() {
            @Override
            public void onMapClicked(ClickData mapClickInfo) {
                if (mapClickInfo.getClickType() == ClickType.CLICK_TYPE_LONG) {
                    clickedLocation = mapClickInfo.getClickPos();
                    LngLat lngLat = new LngLat(clickedLocation.getX(), clickedLocation.getY());
                    addMarker(lngLat);
                }
            }
        });
    }

    private void initLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    private void initSideNavigation() {
        Menu menu = nav.getMenu();
        switch (mapStyle) {
            case NESHAN:
                menu.findItem(R.id.side_nav_theme_neshan).setChecked(true);
                break;
            case STANDARD_NIGHT:
                menu.findItem(R.id.side_nav_theme_standard_night).setChecked(true);
                break;
            case STANDARD_DAY:
                menu.findItem(R.id.side_nav_theme_standard_day).setChecked(true);
                break;
        }
    }

    private void initViews() {
        map = findViewById(R.id.map);
        drawer = findViewById(R.id.drawer_layout);
        nav = findViewById(R.id.side_nav);
        toolbar = findViewById(R.id.toolbar);
        focusOnUserLocationBtn = findViewById(R.id.user_location_btn);
    }

    private void initToolbar() {
        Drawable navIcon = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_dehaze, null);
        toolbar.setNavigationIcon(navIcon);
        setSupportActionBar(toolbar);
    }

    private void initMap() {
        /* getLayers().insert()
         when you insert a layer in index i, index (i - 1) should exist
         keep base map layer at index 0
         ********
         getLayers().add()
         suppose map has k layers right now, new layer adds in index (k + 1)
         */
        userMarkerLayer = NeshanServices.createVectorElementLayer();
        markerLayer = NeshanServices.createVectorElementLayer();
        lineLayer = NeshanServices.createVectorElementLayer();
        polygonLayer = NeshanServices.createVectorElementLayer();
        map.getLayers().add(userMarkerLayer);
        map.getLayers().add(markerLayer);
        map.getLayers().add(lineLayer);
        map.getLayers().add(polygonLayer);

        // add Standard_day map to layers
        mapStyle = RecordKeeper.instance(this).getMapTheme();
        map.getOptions().setZoomRange(new Range(4.5f, 18f));
        map.getLayers().insert(BASE_MAP_INDEX, NeshanServices.createBaseMap(mapStyle));

        //set map focus position
        map.setFocalPointPosition(new LngLat(53.529929, 35.164676), 0f);
        map.setZoom(4.5f, 0f);

        //Iran Bound
        map.getOptions().setPanBounds(new Bounds(
                new LngLat(43.505859, 24.647017),
                new LngLat(63.984375, 40.178873))
        );
    }

    private boolean getLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    private void getLastLocation() {
        fusedLocationClient
                .getLastLocation()
                .addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            onLocationChange(task.getResult());
                            Log.i(TAG, "lat "+task.getResult().getLatitude()+" lng "+task.getResult().getLongitude());
                        } else {
                            Toast.makeText(MainActivity.this, "موقعیت یافت نشد.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void onLocationChange(Location location) {
        this.userLocation = location;
        addUserMarker(new LngLat(userLocation.getLongitude(), userLocation.getLatitude()));
        map.setFocalPointPosition(
                new LngLat(userLocation.getLongitude(), userLocation.getLatitude()),
                0.25f);
        map.setZoom(15, 0.25f);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                drawer.openDrawer(GravityCompat.START);
                return true;
            case R.id.clear_map:
                markerLayer.clear();
                lineLayer.clear();
                polygonLayer.clear();
                return true;
            case R.id.reset_camera_bearing:
                map.setBearing(0f, 0.3f);
                return true;
            case R.id.reset_camera_tilt:
                map.setTilt(90f, 0.4f);
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        Intent intent = new Intent(MainActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        switch (id) {
            case R.id.side_nav_draw_line:
                drawer.closeDrawer(GravityCompat.START);
                drawLineGeom();
                break;
            case R.id.side_nav_draw_polygon:
                drawer.closeDrawer(GravityCompat.START);
                drawPolygonGeom();
                break;
            case R.id.side_nav_theme_standard_day:
                drawer.closeDrawer(GravityCompat.START);
                map.getLayers().remove(map.getLayers().get(0));
                map.getLayers().insert(0, NeshanServices.createBaseMap(NeshanMapStyle.STANDARD_DAY));
                RecordKeeper.instance(this).setMapTheme(NeshanMapStyle.STANDARD_DAY);
                mapStyle = NeshanMapStyle.STANDARD_DAY;
                initSideNavigation();
                break;
            case R.id.side_nav_theme_standard_night:
                drawer.closeDrawer(GravityCompat.START);
                map.getLayers().remove(map.getLayers().get(0));
                map.getLayers().insert(0, NeshanServices.createBaseMap(NeshanMapStyle.STANDARD_NIGHT));
                RecordKeeper.instance(this).setMapTheme(NeshanMapStyle.STANDARD_NIGHT);
                mapStyle = NeshanMapStyle.STANDARD_NIGHT;
                initSideNavigation();
                break;
            case R.id.side_nav_theme_neshan:
                drawer.closeDrawer(GravityCompat.START);
                map.getLayers().remove(map.getLayers().get(0));
                map.getLayers().insert(0, NeshanServices.createBaseMap(NeshanMapStyle.NESHAN));
                RecordKeeper.instance(this).setMapTheme(NeshanMapStyle.NESHAN);
                mapStyle = NeshanMapStyle.NESHAN;
                initSideNavigation();
                break;
            case R.id.side_nav_project_src:
                drawer.closeDrawer(GravityCompat.START, false);
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NeshanMaps/kotlin-neshan-maps-sample")));
                break;
            case R.id.side_nav_about:
                drawer.closeDrawer(GravityCompat.START, false);
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.neshan.org/")));
                break;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                updateCurrentLocation();
            } else {
                Log.d(TAG, "Permission Denied :(");
            }

        }
    }

    private void addMarker(LngLat loc) {
        markerLayer.clear();

        AnimationStyleBuilder animStBl = new AnimationStyleBuilder();
        animStBl.setFadeAnimationType(AnimationType.ANIMATION_TYPE_SMOOTHSTEP);
        animStBl.setSizeAnimationType(AnimationType.ANIMATION_TYPE_SPRING);
        animStBl.setPhaseInDuration(0.5f);
        animStBl.setPhaseOutDuration(0.5f);
        AnimationStyle animSt = animStBl.buildStyle();

        MarkerStyleCreator markStCr = new MarkerStyleCreator();
        markStCr.setSize(20f);
        markStCr.setBitmap(BitmapUtils.createBitmapFromAndroidBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_marker)));
        markStCr.setAnimationStyle(animSt);
        MarkerStyle markSt = markStCr.buildStyle();

        Marker marker = new Marker(loc, markSt);
        markerLayer.add(marker);
    }

    private void addUserMarker(LngLat loc) {
        userMarkerLayer.clear();

        AnimationStyleBuilder animStBl = new AnimationStyleBuilder();
        animStBl.setFadeAnimationType(AnimationType.ANIMATION_TYPE_SMOOTHSTEP);
        animStBl.setSizeAnimationType(AnimationType.ANIMATION_TYPE_SPRING);
        animStBl.setPhaseInDuration(0.5f);
        animStBl.setPhaseOutDuration(0.5f);
        AnimationStyle animSt = animStBl.buildStyle();

        MarkerStyleCreator markStCr = new MarkerStyleCreator();
        markStCr.setSize(20f);
        markStCr.setBitmap(BitmapUtils.createBitmapFromAndroidBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_marker)));
        markStCr.setAnimationStyle(animSt);
        MarkerStyle markSt = markStCr.buildStyle();

        Marker marker = new Marker(loc, markSt);

        userMarkerLayer.add(marker);
    }

    private LineGeom drawLineGeom() {
        lineLayer.clear();
        LngLatVector lngLatVector = new LngLatVector();
        lngLatVector.add(new LngLat(59.540182, 36.314163));
        lngLatVector.add(new LngLat(59.539290, 36.310654));
        LineGeom lineGeom = new LineGeom(lngLatVector);
        Line line = new Line(lineGeom, getLineStyle());
        lineLayer.add(line);

        // variable for creating bound
        // for this sample
        // min = south-west -> lat = 36.314163, lng = 59.539290
        // max = north-east -> lat = 36.310654, lng = 59.540182
        map.moveToCameraBounds(
                new Bounds(new LngLat(59.539290,  36.314163), new LngLat(59.540182, 36.310654)),
                new ViewportBounds(
                        new ViewportPosition(0,0),
                        new ViewportPosition(map.getWidth(),map.getHeight())
                ),
                true, 0.25f);
        return lineGeom;
    }

    private LineStyle getLineStyle() {
        LineStyleCreator lineStCr = new LineStyleCreator();
        lineStCr.setColor(new ARGB((short) 2, (short) 119, (short) 189, (short) 190));
        lineStCr.setWidth(12f);
        lineStCr.setStretchFactor(0f);
        return lineStCr.buildStyle();
    }

    private PolygonGeom drawPolygonGeom() {
        polygonLayer.clear();
        LngLatVector lngLatVector = new LngLatVector();
        lngLatVector.add(new LngLat(59.55231, 36.30745));
        lngLatVector.add(new LngLat(59.54819, 36.31044));
        lngLatVector.add(new LngLat(59.55079, 36.31296));
        lngLatVector.add(new LngLat(59.55504, 36.31016));
        PolygonGeom polygonGeom = new PolygonGeom(lngLatVector);
        Polygon polygon = new Polygon(polygonGeom, getPolygonStyle());
        polygonLayer.add(polygon);

        // variable for creating bound
        // for this sample
        // min = south-west -> lat = 36.31296, lng = 59.54819
        // max = north-east -> lat = 36.30745, lng = 59.55504
        map.moveToCameraBounds(
                new Bounds(new LngLat(59.54819,  36.31296), new LngLat(59.55504, 36.30745)),
                new ViewportBounds(
                        new ViewportPosition(0,0),
                        new ViewportPosition(map.getWidth(),map.getHeight())
                ),
                true, 0.25f);
        return polygonGeom;
    }

    private PolygonStyle getPolygonStyle() {
        PolygonStyleCreator polygonStCr = new PolygonStyleCreator();
        polygonStCr.setLineStyle(getLineStyle());
        return polygonStCr.buildStyle();
    }
}