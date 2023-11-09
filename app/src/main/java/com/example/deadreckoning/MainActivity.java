package com.example.deadreckoning;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    // UI
    TextView txtSteps;
    private EditText edtHeight;
    private Button btnStart, btnStop;

    // variables
    float alpha = 0.97f;
    double height = 1.65;
    double stepLength = 0.5;
    boolean registered = false;
    Point current = new Point(0,0);
    int ignoreCnt = 0;

    // check first steps - noise cancelling
    int firstCheck = 0;

    // Sensors
    private SensorManager sensorManager;
    private Sensor accelerometerSensor;
    private float[] accelerometerData = new float[3];
    private static final double MIN_STEP_THRESHOLD = 9.4; //9.5 Minimum value for peak detection
    private static final double STEP_THRESHOLD = 10.5; // Threshold for peak detection
    private boolean isPeak = false;
    int stepCount = 0;

    // Yaw, pitch roll
    private float Rot[] = null; //for gravity rotational data
    private float I[] = null; //for magnetic rotational data
    private float accels[] = new float[3];
    private float mags[] = new float[3];
    private float[] values = new float[3];
    private float yaw, originYaw = 85; // TODO
    private ArrayList<Float> yaws = new ArrayList<>();
    private float pitch;
    private float roll;
    private Sensor rotationSensor;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // UI
        txtSteps = findViewById(R.id.txtSteps);
        edtHeight = findViewById(R.id.edtHeight);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    height = Double.parseDouble(edtHeight.getText().toString());
                    stepLength = calculateStrideLength(height);
                    if ( !registered) {
                        registerSensors();
                    }
                } catch (Exception ex) {
                    Toast.makeText(getApplicationContext(), "Empty inputs, fill and try again", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (registered){
                    unregisterSensors();
                }
            }
        });


    }

    private void unregisterSensors() {
        sensorManager.unregisterListener(this);
    }

    private void registerSensors() {
        if (accelerometerSensor!=null){
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            sensorManager.registerListener(this, magneticField,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        registered = true;
    }


    public static double calculateStrideLength(double heightInMeter) {
        double minHeightPercentage = 0.45; // 45% of height
        double maxHeightPercentage = 0.55; // 55% of height

        double minStrideLength = heightInMeter * minHeightPercentage;
        double maxStrideLength = heightInMeter * maxHeightPercentage;

        // Calculate average stride length using the range
        double averageStrideLength = (minStrideLength + maxStrideLength) / 2;
        return averageStrideLength;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }


    @Override
    protected void onPause() {
        super.onPause();
            sensorManager.unregisterListener(this);
            registered = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (firstCheck<10){
            firstCheck++;
            return;
        }

        switch (event.sensor.getType())
        {
            case Sensor.TYPE_MAGNETIC_FIELD:
                if (mags == null) {
                    mags = event.values.clone();
                    break;
                }
                mags[0] = alpha*mags[0] + (1-alpha)*event.values[0];
                mags[1] = alpha*mags[1] + (1-alpha)*event.values[1];
                mags[2] = alpha*mags[2] + (1-alpha)*event.values[2];

                break;
            case Sensor.TYPE_ACCELEROMETER:
                if (accels == null) {
                    accels = event.values.clone();
                    break;
                }
                accels[0] = alpha*accels[0] + (1-alpha)*event.values[0];
                accels[1] = alpha*accels[1] + (1-alpha)*event.values[1];
                accels[2] = alpha*accels[2] + (1-alpha)*event.values[2];
                break;
        }

        // Step
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            accelerometerData[0] = event.values[0];
            accelerometerData[1] = event.values[1];
            accelerometerData[2] = event.values[2];


            // Calculate magnitude of combined sensor data
            double magnitude = Math.sqrt(
                    accelerometerData[0] * accelerometerData[0] +
                            accelerometerData[1] * accelerometerData[1] +
                            accelerometerData[2] * accelerometerData[2]
            );


            txtSteps.setText( "Yaw = " + yaw+"\n"+
                            "mag = " + magnitude +"\n"+
                    "steps = " + stepCount + "\n"+
                    "cx = "+  current.getX() + "\n"+
                    "cy = " + current.getY());

            // Detect peaks
            if (magnitude > STEP_THRESHOLD && !isPeak) {
                // Detected a peak (potential step)
                isPeak = true;
            } else if (magnitude < MIN_STEP_THRESHOLD && isPeak) {
                // Step has ended
                isPeak = false;
                // Increment step count here
                stepCount++;
                // update current
                updateCurrent();

            }
        }

        // Rotation
        if (mags != null && accels != null) {

            Rot = new float[9];
            I= new float[9];
            SensorManager.getRotationMatrix(Rot, I, accels, mags);

            SensorManager.getOrientation(Rot, values);

            // here we calculated the final yaw(azimuth), roll & pitch of the device.
            // multiplied by a global standard value to get accurate results

            // this is the yaw or the azimuth we need
            modifyYaw(values[0]);
            pitch = (float)Math.toDegrees(values[1]);
            roll = (float)Math.toDegrees(values[2]);

            //retrigger the loop when things are repopulated
            mags = null;
            accels = null;
        }

    }

    private void updateCurrent() {
        current.x += stepLength * Math.sin(Math.toRadians(yaw-originYaw));
        current.y += stepLength * Math.cos(Math.toRadians(yaw-originYaw));
    }


    private void modifyYaw(float value) {
        if (yaws.size()==0){
            yaw = (float)Math.toDegrees(value);
            yaws.add(yaw);
        }else{
            yaw = 0;
            for (int i = 0; i < yaws.size(); i++) {
                if ( yaws.get(i)<-170 || yaws.get(i)>170) {
                    if (yaws.get(0)>0){
                        yaw += Math.abs(yaws.get(i));
                    }else{
                        yaw += -Math.abs(yaws.get(i));
                    }
                }else{
                    yaw += yaws.get(i);
                }
            }
            yaw = yaw/yaws.size();
            if (ignoreCnt<=5 && Math.abs(yaw - (float)Math.toDegrees(value) )>20) {
                ignoreCnt++;
            }else if (ignoreCnt > 5){
                ignoreCnt = 0;
                yaws.remove(0);
                yaws.add((float)Math.toDegrees(value));
                yaw = 0;
                for (int i = 0; i < yaws.size(); i++) {
                    if ( yaws.get(i)<-170 || yaws.get(i)>170) {
                        if (yaws.get(0)>0){
                            yaw += Math.abs(yaws.get(i));
                        }else{
                            yaw += -Math.abs(yaws.get(i));
                        }
                    }else{
                        yaw += yaws.get(i);
                    }
                }
                yaw = yaw/yaws.size();
            }else{
                if (yaws.size()>5){
                    yaws.remove(0);
                }
                yaws.add((float)Math.toDegrees(value));
                yaw = 0;
                for (int i = 0; i < yaws.size(); i++) {
                    if ( yaws.get(i)<-170 || yaws.get(i)>170) {
                        if (yaws.get(0)>0){
                            yaw += Math.abs(yaws.get(i));
                        }else{
                            yaw += -Math.abs(yaws.get(i));
                        }
                    }else{
                        yaw += yaws.get(i);
                    }
                }
                yaw /= yaws.size();
            }

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}