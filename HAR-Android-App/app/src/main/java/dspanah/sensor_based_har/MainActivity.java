package dspanah.sensor_based_har;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.speech.tts.TextToSpeech;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TableRow;
import android.widget.TextView;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import org.apache.commons.lang3.ArrayUtils;
import java.lang.Math;

import static java.util.Collections.max;
import static java.util.Collections.min;

public class MainActivity extends AppCompatActivity implements SensorEventListener, TextToSpeech.OnInitListener {

    private static final int N_SAMPLES = 80;
    private static int prevIdx = -1;
    private static int start = 0;
    private static int end = N_SAMPLES;
    private boolean first = true;

    private static List<Float> ax;
    private static List<Float> ay;
    private static List<Float> az;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mLinearAcceleration;

    private TextView flickTextView;
    private TextView leftTextView;
    private TextView openTextView;
    private TextView rightTextView;
    private TextView waveTextView;

    private TableRow flickTableRow;
    private TableRow leftTableRow;
    private TableRow openTableRow;
    private TableRow rightTableRow;
    private TableRow waveTableRow;

    private float mAccel;
    private float mAccelCurrent;
    private float mAccelLast;

    private TextToSpeech textToSpeech;
    private float[] results;
    boolean gating = false;
    String currentAxis = "";

    String modelFile="watch_quantized_click.tflite";
    Interpreter tflite;
    List<List<Float>> next = new ArrayList<>();


    //private String[] labels = {"Down", "Left", "Right", "Up"};
    private String[] labels = {"Flick", "Left", "Open", "Right", "Wave"};
    private float[] mGravity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ax = new ArrayList<>(); ay = new ArrayList<>(); az = new ArrayList<>();

        flickTextView = (TextView) findViewById(R.id.flick_prob);
        leftTextView = (TextView) findViewById(R.id.left_prob);
        openTextView = (TextView) findViewById(R.id.open_prob);
        rightTextView = (TextView) findViewById(R.id.right_prob);
        waveTextView = (TextView) findViewById(R.id.wave_prob);

        flickTableRow = (TableRow) findViewById(R.id.flick_row);
        leftTableRow = (TableRow) findViewById(R.id.left_row);
        openTableRow = (TableRow) findViewById(R.id.open_row);
        rightTableRow = (TableRow) findViewById(R.id.right_row);
        waveTableRow = (TableRow) findViewById(R.id.wave_row);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mAccel = 0.00f;
        mAccelCurrent = SensorManager.GRAVITY_EARTH;
        mAccelLast = SensorManager.GRAVITY_EARTH;

        mSensorManager.registerListener(this, mAccelerometer , 83000);

        try {
            tflite = new Interpreter(loadModelFile(this, modelFile));
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        textToSpeech = new TextToSpeech(this, this);
        textToSpeech.setLanguage(Locale.US);
    }

    private MappedByteBuffer loadModelFile(Activity activity, String MODEL_FILE) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    @Override
    public void onInit(int status) {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (results == null || results.length == 0) {
                    return;
                }
                float max = -1;
                int idx = -1;
                for (int i = 0; i < results.length; i++) {
                    if (results[i] > max) {
                        idx = i;
                        max = results[i];
                    }
                }

                if(max > 0.10 && idx != prevIdx) {
                    textToSpeech.speak(labels[idx], TextToSpeech.QUEUE_ADD, null,
                            Integer.toString(new Random().nextInt()));
                    prevIdx = idx;
                }
            }
        }, 1000, 3000);
    }

    protected void onResume() {
        super.onResume();
        getSensorManager().registerListener(this, getSensorManager().getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
     }

    @Override
    public void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        Sensor sensor = event.sensor;
        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            //System.out.println((event.values[0]));


            if (event.values[0] < 2 && event.values[1] < 1 && event.values[2] > 9.2) {
                gating = true;
//                currentAxis = "z";
                ax.add(event.values[0]);
                ay.add(event.values[1]);
                az.add(event.values[2]);
            }else if (event.values[0] < 4 && event.values[1] < -8 && event.values[2] < 2){
                gating = true;
//                currentAxis = "negY";
                ax.add(event.values[0]);
                ay.add(event.values[2]);
                az.add(Math.abs(event.values[1]));
            }else if (event.values[0] < 0 && event.values[1] > 8 && event.values[2] < 3.5){
                gating = true;
//                currentAxis = "posY";
                ax.add(event.values[0]);
                ay.add(event.values[2]);
                az.add(event.values[1]);
            }else{
                gating = false;
                ax.clear();
                ay.clear();
                az.clear();
                System.out.println("Clear");
            }

//            if (gating) {
//                //System.out.println("here");
//                ax.add(event.values[0]);
//                ay.add(event.values[1]);
//                az.add(event.values[2]);
//
//            }
            mGravity = event.values.clone();

            activityPrediction();
        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void activityPrediction() {

        List<List<Float>> data = new ArrayList<>();

        if (ax.size() >= N_SAMPLES && ay.size() >= N_SAMPLES && az.size() >= N_SAMPLES) {
            gating = false;
            //if((max(ax) > 2 || min(ax) < -2) ||(max(ay) > 2 || min(ay) < -2) ||
            //(max(az) > 12 || min(az) < 8)){

            data.add(ax.subList(1, N_SAMPLES));
            data.add(ay.subList(1, N_SAMPLES));
            data.add(az.subList(1, N_SAMPLES));

            data = transpose(data);
//            System.out.println("Data" + data);

            float[][] array = data.stream().map(u -> ArrayUtils.toPrimitive(u.toArray(new Float[0]))).toArray(float[][]::new);
//            System.out.println("Array" + Arrays.toString(array));

            float[][][] inp = new float[1][array.length][3];
//            System.out.println("inp" + Arrays.toString(inp));

            inp[0] = array;

            float[][] out = new float[][]{{0, 0, 0, 0, 0}};
            //float[][] out = new float[][]{{0, 0, 0, 0}};
//            tflite.run(inp, out);
//            System.out.println("out" + Arrays.toString(out));
//            results = out[0];

//            float max = -1;
//            int idx = -1;
//            float second = -1;
//            for (int i = 0; i < results.length; i++) {
//                System.out.println(Arrays.toString(results));
//                if (results[i] > max) {
//                    idx = i;
//                    max = results[i];
//                }
//                if (results[i] > 0.15 && results[i] != max) {
//                    second = results[i];
//                }
//            }

//            if (max >= 0.95 && second == -1) {
                System.out.print("inp: ");
                System.out.println(Arrays.deepToString(inp[0]));

//                System.out.print("result: ");
                //System.out.println(Arrays.toString(results));
//                System.out.println(idx);
//            } else {
//                System.out.println(-1);
//            }

//            setProbabilities();
//            setRowsColor(idx);

            ax.clear();
            ay.clear();
            az.clear();
        }
    }

    public static List<List<Float>> transpose(List<List<Float>> matrixIn) {
        List<List<Float>> matrixOut = new ArrayList<>();
        if (!matrixIn.isEmpty()) {
            int noOfElementsInList = matrixIn.get(0).size();
            for (int i = 0; i < noOfElementsInList; i++) {
                List<Float> col = new ArrayList<Float>();
                for (List<Float> row : matrixIn) {
                    col.add(row.get(i));
                }
                matrixOut.add(col);
            }
        }

        return matrixOut;
    }

    private void setProbabilities() {
        flickTextView.setText(Float.toString(round(results[0], 2)));
        leftTextView.setText(Float.toString(round(results[1], 2)));
        openTextView.setText(Float.toString(round(results[2], 2)));
        rightTextView.setText(Float.toString(round(results[3], 2)));
        waveTextView.setText(Float.toString(round(results[4], 2)));
    }

    private void setRowsColor(int idx) {
        flickTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorTransparent, null));
        leftTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorTransparent, null));
        openTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorTransparent, null));
        rightTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorTransparent, null));
        waveTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorTransparent, null));

        if(idx == 0)
            flickTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorBlue, null));
        else if (idx == 1)
            leftTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorBlue, null));
        else if (idx == 2)
            openTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorBlue, null));
        else if (idx == 3)
            rightTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorBlue, null));
        else if (idx == 4)
            waveTableRow.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.colorBlue, null));

    }

    private float[] toFloatArray(List<Float> list) {
        int i = 0;
        float[] array = new float[list.size()];

        for (Float f : list) {
            array[i++] = (f != null ? f : Float.NaN);
        }
        return array;
    }

    private static float round(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }

    private SensorManager getSensorManager() {
        return (SensorManager) getSystemService(SENSOR_SERVICE);
    }

}