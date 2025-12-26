package com.example.shambaletu;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class registration extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        // Find the TextView by its ID
        TextView loginLink = findViewById(R.id.loginLink);

        // Set a click listener on the TextView
        loginLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create an Intent to start the loginpage activity
                Intent intent = new Intent(registration.this, loginpage.class);
                startActivity(intent);
            }
        });
    }
}