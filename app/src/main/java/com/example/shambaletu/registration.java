package com.example.shambaletu;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class registration extends AppCompatActivity {

    private EditText etFirstName, etLastName, etEmail, etPhone, etPassword;
    private Button registerBtn;
    private FirebaseAuth fAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        // Initialize Firebase Auth
        fAuth = FirebaseAuth.getInstance();

        // Bind Views - These must match the IDs in activity_registration.xml
        TextView loginLink = findViewById(R.id.loginLink);
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etemailAddress);
        etPhone = findViewById(R.id.etphoneNumber);
        etPassword = findViewById(R.id.etpassword);
        registerBtn = findViewById(R.id.registerbtn);

        // Redirect to Login if clicked
        loginLink.setOnClickListener(v -> {
            startActivity(new Intent(registration.this, loginpage.class));
            finish();
        });

        registerBtn.setOnClickListener(v -> {
            String firstName = etFirstName.getText().toString().trim();
            String lastName = etLastName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            // Validation logic
            if (TextUtils.isEmpty(firstName)) {
                etFirstName.setError("First name is required");
                return;
            }
            if (TextUtils.isEmpty(lastName)) {
                etLastName.setError("Last name is required");
                return;
            }
            if (TextUtils.isEmpty(email)) {
                etEmail.setError("Email is required");
                return;
            }
            if (TextUtils.isEmpty(phone)) {
                etPhone.setError("Phone number is required");
                return;
            }
            if (TextUtils.isEmpty(password)) {
                etPassword.setError("Password is required");
                return;
            }
            if (password.length() < 6) {
                etPassword.setError("Password must be at least 6 characters");
                return;
            }

            // Register the user in Firebase
            fAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(registration.this, "User Registered Successfully.", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(getApplicationContext(), loginpage.class));
                    finish();
                } else {
                    String errorMessage = task.getException() != null ? task.getException().getMessage() : "Registration failed";
                    Toast.makeText(registration.this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
