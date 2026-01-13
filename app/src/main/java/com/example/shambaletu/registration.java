package com.example.shambaletu;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Firebase;
import com.google.firebase.auth.FirebaseAuth;

public class registration extends AppCompatActivity {

   EditText etfirstName,etlastName,etemailAddress,etphoneNumber,etpassword;
   Button registerBtn;
   FirebaseAuth fAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        // Find the TextView by its ID
        TextView loginLink = findViewById(R.id.loginLink);
        etfirstName = findViewById(R.id.etFirstName);
        etlastName = findViewById(R.id.etLastName);
        etemailAddress = findViewById(R.id.etemailAddress);
        etphoneNumber = findViewById(R.id.etphoneNumber);
        etpassword = findViewById(R.id.etpassword);
        registerBtn = findViewById(R.id.registerbtn);
        fAuth = FirebaseAuth.getInstance();


        // Set a click listener on the TextView
        loginLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create an Intent to start the loginpage activity
                Intent intent = new Intent(registration.this, loginpage.class);
                startActivity(intent);
                finish();
            }
        });

        registerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String firstName = etfirstName.getText().toString().trim();
                String lastName = etlastName.getText().toString().trim();
                String email = etemailAddress.getText().toString().trim();
                String phoneNumber = etphoneNumber.getText().toString().trim();
                String password = etpassword.getText().toString().trim();

                if(fAuth.getCurrentUser() != null){
                    Intent intent = new Intent(registration.this, loginpage.class);
                    startActivity(intent);
                    finish();
                }

                if(firstName.isEmpty()){
                    etfirstName.setError("Firstname is required");
                    return;
                }
                if(lastName.isEmpty()){
                    etlastName.setError("Lastname is required");
                    return;
                }
                if(email.isEmpty()){
                    etemailAddress.setError("Email is required");
                    return;
                }
                if(phoneNumber.isEmpty()){
                    etphoneNumber.setError("Phone number  is required");
                    return;
                }
                if(password.isEmpty()){
                    etpassword.setError("Password is required");
                    return;
                }
                if(password.length() < 6){
                    etpassword.setError("Password must be at least 6 characters to sign up");
                }
                fAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task ->{
                    if(task.isSuccessful()){
                        Toast.makeText(registration.this, "Registered successfully", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(registration.this, loginpage.class);
                        startActivity(intent);
                        finish();
                    }else {
                        Toast.makeText(registration.this, "Failed to register", Toast.LENGTH_SHORT).show();
                    }
                }  );

            }
        });


        // Write a message to the database
        //val database = Firebase.database;
       // val myRef = database.getReference("message");

       // myRef.setValue("Hello, World!");
    }
}