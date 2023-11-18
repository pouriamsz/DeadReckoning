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
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    // UI
    TextView txtSteps;
    private EditText edtHeight;
    private Button btnStart, btnStop, btnSetAzimuth, btnReset;
    RadioButton rMale, rFemale;
    boolean male = true;

    // variables
    float alpha = 0.8f; //TODO
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
    private static final double MIN_STEP_THRESHOLD = 9.6; //TODO: 9.4 // Minimum value for peak detection
    private static final double STEP_THRESHOLD = 10.7; //TODO: 10.5 // Threshold for peak detection
    private boolean isPeak = false;
    int stepCount = 0;

    // Yaw, pitch roll
    private float Rot[] = null; //for gravity rotational data
    private float I[] = null; //for magnetic rotational data
    private float accels[] = new float[3];
    private float mags[] = new float[3];
    boolean getAccels = false, getMags = false;
    private float[] values = new float[3];
    private float yaw, originYaw = 85; // TODO
    private ArrayList<Float> yaws = new ArrayList<>();
    private float pitch;
    private float roll;
    private Sensor rotationSensor, gyroscopeSensor;
    float gyroY = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // UI
        txtSteps = findViewById(R.id.txtSteps);
        edtHeight = findViewById(R.id.edtHeight);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnSetAzimuth = findViewById(R.id.btnSetAzimuth);
        btnReset = findViewById(R.id.btnReset);
        rMale = findViewById(R.id.radioMale);
        rFemale = findViewById(R.id.radioFemale);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        rMale.setOnCheckedChangeListener((buttonView, isChecked) -> male = isChecked);

        rFemale.setOnCheckedChangeListener((buttonView, isChecked) -> male = !isChecked);

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepCount = 0;
                current.setX(0.0);
                current.setY(0.0);
            }
        });

        btnSetAzimuth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                originYaw = yaw;
            }
        });

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
        if (gyroscopeSensor!=null){
            sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        registered = true;
    }


    public double calculateStrideLength(double heightInMeter) {
        double heightPercentage = 0.3;
        if (male){
            heightPercentage = 0.3;
        }else{
            heightPercentage = 0.28;
        }

        double strideLength = heightInMeter * heightPercentage;

        return strideLength;
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

        gyroscope(event);
        getSensorData(event);
        rotation();
        stepDetector(event);
    }

    private void gyroscope(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE){
            float rotationX = event.values[0];
            gyroY = event.values[1];
            float rotationZ = event.values[2];
//            txtSteps.setText( "Yaw = " + yaw+"\n"+
//                    "steps = " + stepCount + "\n"+
//                    "gyro x = " + rotationX + "\n" +
//                    "gyro y = " + rotationY + "\n" +
//                    "gyro z = " + rotationZ + "\n"
//            );
        }

    }

    private void rotation() {
        // Rotation
        if (getMags && getAccels) {

            Rot = new float[9];
            I= new float[9];
            SensorManager.getRotationMatrix(Rot, I, accels, mags);
            float[] outR = new float[9];
            SensorManager.remapCoordinateSystem(Rot, SensorManager.AXIS_X,SensorManager.AXIS_Z, outR);
            SensorManager.getOrientation(outR, values);

            // here we calculated the final yaw(azimuth), roll & pitch of the device.
            // multiplied by a global standard value to get accurate results

            double mAzimuthAngleNotFlat = Math.toDegrees(Math
                    .atan2((outR[1] - outR[3]), (outR[0] + outR[4])));

            mAzimuthAngleNotFlat += 180;
            // this is the yaw or the azimuth we need
            modifyYaw((float)mAzimuthAngleNotFlat);
            pitch = (float)Math.toDegrees(values[1]);
            roll = (float)Math.toDegrees(values[2]);

            getAccels = false;
            getMags = false;
        }
    }

    private void stepDetector(SensorEvent event) {
        // Step
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){

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
    }

    private void getSensorData(SensorEvent event) {
        switch (event.sensor.getType())
        {
            case Sensor.TYPE_MAGNETIC_FIELD:
                mags[0] = alpha*mags[0] + (1-alpha)*event.values[0];
                mags[1] = alpha*mags[1] + (1-alpha)*event.values[1];
                mags[2] = alpha*mags[2] + (1-alpha)*event.values[2];
                getMags = true;
                break;
            case Sensor.TYPE_ACCELEROMETER:
                accelerometerData[0] = event.values[0];
                accelerometerData[1] = event.values[1];
                accelerometerData[2] = event.values[2];

                accels[0] = alpha*accels[0] + (1-alpha)*event.values[0];
                accels[1] = alpha*accels[1] + (1-alpha)*event.values[1];
                accels[2] = alpha*accels[2] + (1-alpha)*event.values[2];
                getAccels = true;
                break;
        }
    }

    private void updateCurrent() {
        current.x += stepLength * Math.sin(Math.toRadians(yaw-originYaw));
        current.y += stepLength * Math.cos(Math.toRadians(yaw-originYaw));
    }


    private void modifyYaw(float value) {
        if (yaws.size() >= 5 && Math.abs(gyroY) < 0.2){ // TODO
            return;
        }
        if (Math.abs(gyroY)>0.5){
            yaws = new ArrayList<>();
            ignoreCnt = 0;
        }
        if ((yaw > 340 && value < 20) || (yaw<20 && value>340)){
            yaws = new ArrayList<>();
        }
        if (yaws.size()==0){
            yaw = value;
            yaws.add(yaw);
        }else{
            yaw = 0;
            for (int i = 0; i < yaws.size(); i++) {
                    yaw += yaws.get(i);
            }
            yaw = yaw/yaws.size();
            if (ignoreCnt<=5 && Math.abs(yaw - value )>10 && gyroY<0.2) { //TODO
                ignoreCnt++;
            }else if (ignoreCnt > 5){ // TODO
                ignoreCnt--; // TODO
                yaws.remove(0);
                yaws.add(value);
                yaw = 0;
                for (int i = 0; i < yaws.size(); i++) {
                        yaw += yaws.get(i);
                }
                yaw = yaw/yaws.size();
            }else{
                if (yaws.size()>5){ //TODO
                    yaws.remove(0);
                }
                yaws.add(value);
                yaw = 0;
                for (int i = 0; i < yaws.size(); i++) {
                        yaw += yaws.get(i);
                }
                yaw /= yaws.size();
            }

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
