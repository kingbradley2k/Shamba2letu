package com.example.shambaletu;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class loginpage extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loginpage);

        // Find the TextView by its correct ID
        TextView registerLink = findViewById(R.id.signuplink);

        // Set a click listener on the TextView
        registerLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create an Intent to start the registration activity
                Intent intent = new Intent(loginpage.this, registration.class);
                startActivity(intent);
            }
        });
    }
}