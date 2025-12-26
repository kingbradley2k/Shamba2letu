package com.example.shambaletu;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find the button by its ID
        Button getStartedButton = findViewById(R.id.getStartedButton);

        // Set a click listener on the button
        getStartedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create an Intent to start the registration activity
                Intent intent = new Intent(MainActivity.this, registration.class);
                startActivity(intent);
            }
        });
    }
}