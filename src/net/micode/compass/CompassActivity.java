/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.compass;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class CompassActivity extends Activity {

	private final float MAX_ROATE_DEGREE = 1.0f;
	private SensorManager mSensorManager;
	private Sensor mOrientationSensor;
	private LocationManager mLocationManager;
	private String mLocationProvider;
	private float mDirection;
	private float mTargetDirection;
	private AccelerateInterpolator mInterpolator;
	protected final Handler mHandler = new Handler();
	private boolean mStopDrawing;
	private boolean mChinese;

	View mCompassView;
	CompassView mPointer;
	TextView mLocationTextView;
	LinearLayout mDirectionLayout;
	LinearLayout mAngleLayout;

	// 实现接口
	protected Runnable mCompassViewUpdater = new Runnable() {
		@Override
		public void run() {
			if (mPointer != null && !mStopDrawing) {
				if (mDirection != mTargetDirection) {

					// calculate the shortest routine
					float to = mTargetDirection;
					if (to - mDirection > 180) {
						to -= 360;
					} else if (to - mDirection < -180) {
						to += 360;
					}

					// limit the max speed to MAX_ROTATE_DEGREE
					float distance = to - mDirection;
					if (Math.abs(distance) > MAX_ROATE_DEGREE) {
						distance = distance > 0 ? MAX_ROATE_DEGREE
								: (-1.0f * MAX_ROATE_DEGREE);
					}

					// need to slow down if the distance is short
					// 无参构造函数下mFactor=1.0f，那么getInterpolation(float input)返回的是input * input
					// 每次distance并不相同，因此每次的返回值有差别，导致mDirection的增加速度不同，mPointer的旋转速度也就不同。一直到mDirection=mTargetDirection
					mDirection = normalizeDegree(mDirection
							+ ((to - mDirection) * mInterpolator
									.getInterpolation(Math.abs(distance) > MAX_ROATE_DEGREE ? 0.4f
											: 0.3f)));
					mPointer.updateDirection(mDirection);
				}

				//mPointer旋转结束之后更新布局中的一些显示
				updateDirection();

				// postDelayed周期性更新UI，在这里就是每20ms更新mpointer的位置
				mHandler.postDelayed(mCompassViewUpdater, 20);
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		// 把控件的findviewbyid放到一个函数下
		initResources();
		initServices();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mLocationProvider != null) {
			// getLastKnownLocation可根据当前设备情况自动选择哪种location provider
			updateLocation(mLocationManager
					.getLastKnownLocation(mLocationProvider));
			// 实时更新：2000ms,10m
			mLocationManager.requestLocationUpdates(mLocationProvider, 2000,
					10, mLocationListener);
		} else {
			mLocationTextView.setText(R.string.cannot_get_location);
		}
		if (mOrientationSensor != null) {
			// 注册监听事件，实时跟新
			mSensorManager.registerListener(mOrientationSensorEventListener,
					mOrientationSensor, SensorManager.SENSOR_DELAY_GAME);
		}
		mStopDrawing = false;
		// 调用mCompassViewUpdater
		mHandler.postDelayed(mCompassViewUpdater, 20);
	}

	@Override
	protected void onPause() {
		super.onPause();
		mStopDrawing = true;
		if (mOrientationSensor != null) {
			mSensorManager.unregisterListener(mOrientationSensorEventListener);
		}
		if (mLocationProvider != null) {
			mLocationManager.removeUpdates(mLocationListener);
		}
	}

	private void initResources() {
		mDirection = 0.0f;
		mTargetDirection = 0.0f;
		// 插值器，安卓的动画效果，加速减速
		mInterpolator = new AccelerateInterpolator();
		mStopDrawing = true;
		mChinese = TextUtils.equals(Locale.getDefault().getLanguage(), "zh");

		mCompassView = findViewById(R.id.view_compass);
		mPointer = (CompassView) findViewById(R.id.compass_pointer);
		mLocationTextView = (TextView) findViewById(R.id.textview_location);
		mDirectionLayout = (LinearLayout) findViewById(R.id.layout_direction);
		mAngleLayout = (LinearLayout) findViewById(R.id.layout_angle);

		mPointer.setImageResource(mChinese ? R.drawable.compass_cn
				: R.drawable.compass);
	}

	private void initServices() {
		// sensor manager
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mOrientationSensor = mSensorManager
				.getDefaultSensor(Sensor.TYPE_ORIENTATION);

		// location manager
		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		criteria.setAltitudeRequired(false);
		criteria.setBearingRequired(false);
		criteria.setCostAllowed(true);
		criteria.setPowerRequirement(Criteria.POWER_LOW);
		mLocationProvider = mLocationManager.getBestProvider(criteria, true);

	}

	// 更新mDirectionLayout和mAngleLayout
	private void updateDirection() {
		// 设置view的属性：how big the view wants to be for both width and height
		LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT);

		mDirectionLayout.removeAllViews();
		mAngleLayout.removeAllViews();

		ImageView east = null;
		ImageView west = null;
		ImageView south = null;
		ImageView north = null;
		float direction = normalizeDegree(mTargetDirection * -1.0f);
		if (direction > 22.5f && direction < 157.5f) {
			// east
			east = new ImageView(this);
			east.setImageResource(mChinese ? R.drawable.e_cn : R.drawable.e);
			east.setLayoutParams(lp);
		} else if (direction > 202.5f && direction < 337.5f) {
			// west
			west = new ImageView(this);
			west.setImageResource(mChinese ? R.drawable.w_cn : R.drawable.w);
			west.setLayoutParams(lp);
		}

		if (direction > 112.5f && direction < 247.5f) {
			// south
			south = new ImageView(this);
			south.setImageResource(mChinese ? R.drawable.s_cn : R.drawable.s);
			south.setLayoutParams(lp);
		} else if (direction < 67.5 || direction > 292.5f) {
			// north
			north = new ImageView(this);
			north.setImageResource(mChinese ? R.drawable.n_cn : R.drawable.n);
			north.setLayoutParams(lp);
		}

		// addView：Adds a child view
		if (mChinese) {
			// east/west should be before north/south
			if (east != null) {
				mDirectionLayout.addView(east);
			}
			if (west != null) {
				mDirectionLayout.addView(west);
			}
			if (south != null) {
				mDirectionLayout.addView(south);
			}
			if (north != null) {
				mDirectionLayout.addView(north);
			}
		} else {
			// north/south should be before east/west
			if (south != null) {
				mDirectionLayout.addView(south);
			}
			if (north != null) {
				mDirectionLayout.addView(north);
			}
			if (east != null) {
				mDirectionLayout.addView(east);
			}
			if (west != null) {
				mDirectionLayout.addView(west);
			}
		}

		int direction2 = (int) direction;
		boolean show = false;
		if (direction2 >= 100) {
			mAngleLayout.addView(getNumberImage(direction2 / 100));
			direction2 %= 100;
			show = true;
		}
		if (direction2 >= 10 || show) {
			mAngleLayout.addView(getNumberImage(direction2 / 10));
			direction2 %= 10;
		}
		mAngleLayout.addView(getNumberImage(direction2));

		ImageView degreeImageView = new ImageView(this);
		degreeImageView.setImageResource(R.drawable.degree);
		degreeImageView.setLayoutParams(lp);
		mAngleLayout.addView(degreeImageView);
	}

	// 设置mDirectionLayout和mAngleLayout的子view的内容
	private ImageView getNumberImage(int number) {
		ImageView image = new ImageView(this);
		LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT);
		switch (number) {
		case 0:
			image.setImageResource(R.drawable.number_0);
			break;
		case 1:
			image.setImageResource(R.drawable.number_1);
			break;
		case 2:
			image.setImageResource(R.drawable.number_2);
			break;
		case 3:
			image.setImageResource(R.drawable.number_3);
			break;
		case 4:
			image.setImageResource(R.drawable.number_4);
			break;
		case 5:
			image.setImageResource(R.drawable.number_5);
			break;
		case 6:
			image.setImageResource(R.drawable.number_6);
			break;
		case 7:
			image.setImageResource(R.drawable.number_7);
			break;
		case 8:
			image.setImageResource(R.drawable.number_8);
			break;
		case 9:
			image.setImageResource(R.drawable.number_9);
			break;
		}
		image.setLayoutParams(lp);
		return image;
	}

	// 更新mLocationTextView（经纬度）的内容
	private void updateLocation(Location location) {
		if (location == null) {
			mLocationTextView.setText(R.string.getting_location);
		} else {
			// stringbuffer好使。。
			StringBuilder sb = new StringBuilder();
			double latitude = location.getLatitude();
			double longitude = location.getLongitude();

			// getString作用：将args用R.string中的格式显示：<string name="location_north">%s
			// N</string>
			if (latitude >= 0.0f) {
				sb.append(getString(R.string.location_north,
						getLocationString(latitude)));
			} else {
				sb.append(getString(R.string.location_south,
						getLocationString(-1.0 * latitude)));
			}

			sb.append("    ");

			if (longitude >= 0.0f) {
				sb.append(getString(R.string.location_east,
						getLocationString(longitude)));
			} else {
				sb.append(getString(R.string.location_west,
						getLocationString(-1.0 * longitude)));
			}

			// tostring也是相当常用
			mLocationTextView.setText(sb.toString());
		}
	}

	// 设置经纬度的显示格式
	private String getLocationString(double input) {
		int du = (int) input;
		int fen = (((int) ((input - du) * 3600))) / 60;
		int miao = (((int) ((input - du) * 3600))) % 60;
		// valueof：Converts the specified integer to its string
		// representation； "需要转义
		return String.valueOf(du) + "。" + String.valueOf(fen) + "'"
				+ String.valueOf(miao) + "\"";
	}

	private SensorEventListener mOrientationSensorEventListener = new SensorEventListener() {

		// values[0]: Acceleration minus Gx on the x-axis
		@Override
		public void onSensorChanged(SensorEvent event) {
			// 得到mTargetDirection，0-360度
			float direction = event.values[0] * -1.0f;
			mTargetDirection = normalizeDegree(direction);
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}
	};

	private float normalizeDegree(float degree) {
		return (degree + 720) % 360;
	}

	private LocationListener mLocationListener = new LocationListener() {

		// This method is called when a provider is unable to fetch a location
		// or if the provider has recently
		// become available after a period of unavailability.
		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			if (status != LocationProvider.OUT_OF_SERVICE) {
				updateLocation(mLocationManager
						.getLastKnownLocation(mLocationProvider));
			} else {
				mLocationTextView.setText(R.string.cannot_get_location);
			}
		}

		@Override
		public void onProviderEnabled(String provider) {
		}

		@Override
		public void onProviderDisabled(String provider) {
		}

		@Override
		public void onLocationChanged(Location location) {
			updateLocation(location);
		}

	};
}
