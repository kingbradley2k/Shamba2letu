package com.example.shambaletu;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class loginpage extends AppCompatActivity {

    EditText edtEmail;
    EditText edtPassword;
    Button loginButton;
    FirebaseAuth fAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loginpage);

        // Find the TextView by its correct ID
        TextView registerLink = findViewById(R.id.signuplink);
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        loginButton = findViewById(R.id.loginbtn);
        fAuth = FirebaseAuth.getInstance();

        // Set a click listener on the TextView
        registerLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create an Intent to start the registration activity
                Intent intent = new Intent(loginpage.this, registration.class);
                startActivity(intent);
                finish();
            }
        });

        // Set a click listener on the login button
               loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String email = edtEmail.getText().toString().trim();
                String password = edtPassword.getText().toString().trim();

                if(email.isEmpty()){
                    edtEmail.setError("Email is required");
                    return;
                }
                if (password.isEmpty()){
                    edtPassword.setError("Password is required");
                    return;
                }

                fAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
                    if(task.isSuccessful()){
                        Toast.makeText(loginpage.this, "Logged in successfully", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(loginpage.this, MapMeasurementActivity.class);
                        startActivity(intent);
                        finish();
                    }else{
                        Toast.makeText(loginpage.this, "Login failed, email or password is incorrect", Toast.LENGTH_SHORT).show();
                    }
                });
                // Create an Intent to start the registration activity
                //this part was temporary modified to direct users to the dashboard page but in due time we need to change

            }
        });
    }

}
