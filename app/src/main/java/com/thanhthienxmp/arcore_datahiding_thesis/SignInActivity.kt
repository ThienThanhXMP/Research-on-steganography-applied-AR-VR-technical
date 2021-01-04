package com.thanhthienxmp.arcore_datahiding_thesis

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.thanhthienxmp.arcore_datahiding_thesis.databinding.ActivitySignInBinding

class SignInActivity : AppCompatActivity(), View.OnClickListener {

    companion object{
        private const val TAG = "#SignInActivity"
        private const val RC_SIGN_IN = 9001
    }

    // Initialize biding
    private lateinit var binding: ActivitySignInBinding

    // Google Sign in client
    private lateinit var mGoogleSignInClient: GoogleSignInClient

    // Firebase instance variables (not have needed username yet)
    private lateinit var mFirebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set onClickListener for button
        binding.signInButton.setOnClickListener(this)

        // Get instance for Firebase auth
        mFirebaseAuth = FirebaseAuth.getInstance()

        // Configure Google SignIn (GoogleSignInOptions)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        // Apply configure SignIn Options for client
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    override fun onClick(v: View) {
        when(v.id){
            R.id.sign_in_button -> signIn()
        }
    }

    // When click the sign in button, if haven't signed yet, the app will show the list account, have signed in google account before
    // or switching to another intent to login into google account
    private fun signIn() {
        val signInIntent = mGoogleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    // Result from Sign In with Google account
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInApi.getSignIntIntent()
        if(requestCode == RC_SIGN_IN){
            if(resultCode == RESULT_OK){
                // Get Sign In Account from Intent
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                try{
                    // Google Sign In was successful, authentication with Firebase
                    val account = task.getResult(ApiException::class.java) // Get result with error when not response
                    firebaseAuthWithGoogle(account?.idToken)
                }catch (e: ApiException){
                    // Google Sign In failed, update UI appropriately
                    Log.w(TAG, "Google sign in failed", e)
                }
            }
        }
    }

    // Authentication with Firebase by Google account
    private fun firebaseAuthWithGoogle(idToken: String?) {
        // Get credential from account (this help Firebase access the info from this)
        val credential = GoogleAuthProvider.getCredential(idToken,null)
        // Authentication with Firebase
        mFirebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this){
                if(it.isSuccessful){
                    // Sign in success, update UI with the signed-in user's information
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }else{
                    // If sign in fails, display a message to the user
                    Snackbar.make(binding.root, "Authentication Failed", Snackbar.LENGTH_SHORT).show()
                }
            }
    }
}
