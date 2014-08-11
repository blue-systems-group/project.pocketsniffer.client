package edu.buffalo.cse.pocketsniffer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {
    private final String TAG = Utils.getTag(this.getClass());

	private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Log.d(TAG, "========== Starting PocketSniffer MainActivity  ==========");

        mContext = this;

        OUI.initOuiMap(mContext);

        Intent intent = new Intent(MainActivity.this, SnifferService.class);
        mContext.startService(intent);

        Button startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Start button clicked.");
           }
        });
    }
}
