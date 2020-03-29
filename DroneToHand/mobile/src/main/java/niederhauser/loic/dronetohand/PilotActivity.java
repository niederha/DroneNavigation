package niederhauser.loic.dronetohand;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import static android.content.ContentValues.TAG;


public class PilotActivity extends AppCompatActivity {

    private ARDiscoveryDeviceService mSelectedDrone;
    private DroneHandler mDrone;

    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pilot_activity);
        Button takeOffBtn = findViewById(R.id.takeOff);
        Intent mIntent = getIntent();
        mSelectedDrone =  mIntent.getParcelableExtra("DRONE");
        mDrone = new DroneHandler(mSelectedDrone);

        takeOffBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                mDrone.takeOff();
            }
        });
        Button landBtn = findViewById(R.id.land);
        landBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                mDrone.land();
            }
        });
        Button gotoBtn = findViewById(R.id.goToBtn);
        gotoBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                TextView latTxt = findViewById(R.id.latText);
                TextView lngTxt = findViewById(R.id.longText);
                float latitude = Float.valueOf(latTxt.getText().toString());
                float longitude = Float.valueOf(lngTxt.getText().toString());
                Log.e(TAG, "Lat:"+ latitude + " Long:" + longitude);
                mDrone.goTo(latitude,longitude);
            }
        });
    }
}
