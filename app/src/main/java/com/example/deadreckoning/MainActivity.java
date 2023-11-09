package com.example.deadreckoning;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    TextView txtSteps;


    private SensorManager sensorManager;
    private Sensor accelerometerSensor;
    private float[] accelerometerData = new float[3];
    private static final double MIN_STEP_THRESHOLD = 9.4; //9.5 Minimum value for peak detection
    private static final double STEP_THRESHOLD = 10.5; // Threshold for peak detection
    private boolean isPeak = false;
    int stepCount = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        txtSteps = findViewById(R.id.txtSteps);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometerSensor != null) {
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

    }


    @Override
    protected void onPause() {
        super.onPause();
        if (accelerometerSensor != null ) {
            sensorManager.unregisterListener(this, accelerometerSensor);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {


        accelerometerData[0] = event.values[0];
        accelerometerData[1] = event.values[1];
        accelerometerData[2] = event.values[2];


        // Calculate magnitude of combined sensor data
        double magnitude = Math.sqrt(
                accelerometerData[0] * accelerometerData[0] +
                        accelerometerData[1] * accelerometerData[1] +
                        accelerometerData[2] * accelerometerData[2]
        );


        txtSteps.setText( "mag = " + magnitude +"\n"+
                "steps = " + stepCount);

        // Detect peaks
        if (magnitude > STEP_THRESHOLD && magnitude > MIN_STEP_THRESHOLD && !isPeak) {
            // Detected a peak (potential step)
            isPeak = true;
        } else if (magnitude < MIN_STEP_THRESHOLD && isPeak) {
            // Step has ended
            isPeak = false;
            // Increment step count here
            stepCount++;

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
