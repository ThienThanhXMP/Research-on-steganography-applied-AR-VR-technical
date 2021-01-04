package com.thanhthienxmp.arcore_datahiding_thesis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.ar.core.Anchor
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.assets.RenderableSource
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import com.thanhthienxmp.arcore_datahiding_thesis.gltf_handler.Functions
import com.thanhthienxmp.arcore_datahiding_thesis.gltf_handler.GLTFHandler
import com.thanhthienxmp.arcore_datahiding_thesis.model.User

import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    // Sceneform variables
    private lateinit var arFragment: ArFragment
    private var modelLink = "cube.gltf"
    private var renderModel: ModelRenderable? = null

    // Tag/default name
    companion object{
        private const val TAG = "#MainActivity"
        private const val ANONYMOUS = "anonymous"

        // Firebase child
        private const val CHILD_USERS = "users"
        private const val CHILD_ROOT_USER = "rootUser"
        private const val CHILD_MODELS3D = "models3D"
        private const val CHILD_DEFAULT_MODEL = "defaultModel"
        private const val CHILD_GLTF_FILE = "modelGltfFile"
        private const val CHILD_BIN_FILE = "modelBinFile"

        // Storage firebase name
        private const val DEFAULT_MODEL_FILENAME = "cube"
        private const val MODEL_SCALE = 1.5f
    }

    // Google Sign in client
    private lateinit var mGoogleSignInClient: GoogleSignInClient

    // Firebase instance variables
    private lateinit var auth: FirebaseAuth
    private lateinit var mFirebaseUser: FirebaseUser
    private lateinit var database: DatabaseReference
    private lateinit var storage: FirebaseStorage
    private lateinit var storageRef: StorageReference

    // General variables
    private var mUsername: String = ANONYMOUS
    private lateinit var mPhotoUrl: String
    private lateinit var mGoogleAccount: String
    private lateinit var mGoogleUid: String
    private lateinit var mMessageEditText: EditText
    private lateinit var mMessageSendButton: Button
    private lateinit var message: String

    // Object class
    private val gltfHandler: GLTFHandler = GLTFHandler()
    private val funx: Functions = Functions()

    // File holder
    private var gltfModelFile: File = createTempFile(DEFAULT_MODEL_FILENAME,"gltf")
    private var binModelFile: File = createTempFile(DEFAULT_MODEL_FILENAME,"bin")

    // View component
    private lateinit var mHighBitSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toolbar.title = getString(R.string.app_name)
        setSupportActionBar(toolbar)
        mMessageEditText = findViewById(R.id.messageEditText)
        mMessageSendButton = findViewById(R.id.messageSendButton)
        mHighBitSwitch = findViewById(R.id.highBitSwitch)
        mMessageSendButton.isEnabled = false

        /* [Start Configure Sceneform scene] */
        // Inflate layout with FragmentManager for Fragment
        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment
        arFragment.arSceneView.scene.addOnUpdateListener {
            arFragment.onUpdate(it)
        }

        /* [Start Configure Firebase part] */
        // Initialize Firebase Auth
        auth = Firebase.auth

        // Configure Google Sign In Options
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(/* [Start Configure Firebase part] */getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        // Set configure Sign in options for client
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        // If user haven't signed in yet, switch the SignIn Activity
        if(auth.currentUser == null){
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
        }else{
            // Get info of user
            mFirebaseUser = auth.currentUser!!
            mUsername = mFirebaseUser.displayName.toString()
            mGoogleAccount = mFirebaseUser.email!!.split("@").map { it.trim() }.component1()
            mGoogleUid = mFirebaseUser.uid
            mPhotoUrl = if(mFirebaseUser.photoUrl != null) mFirebaseUser.photoUrl.toString() else ""

            /* [Start Configure Firebase database part] */
            /* [Start Configure Storage part] */

            // Initialize the database
            // Initialize the new account to database
            val userPathTree = "$CHILD_USERS/$mGoogleUid"
            val rootPathTree = "$CHILD_USERS/$CHILD_ROOT_USER"
            database = FirebaseDatabase.getInstance().reference
            // storage = Firebase.storage
            storage = Firebase.storage("gs://${getString(R.string.google_storage_bucket)}")

            // Check account have been registered yet
            database.child(userPathTree).addListenerForSingleValueEvent(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    if(!snapshot.hasChildren()){
                        val key = database.child(userPathTree).push().key
                        val user = User(key,mUsername,mGoogleAccount,mPhotoUrl)
                        database.child(userPathTree).setValue(user)
                    }
                }
                override fun onCancelled(snapshot: DatabaseError) {
                    Log.i(TAG, "This account has been registered")
                }
            })

            // Create a storage reference from app
            storageRef = storage.reference.child(rootPathTree)
            val databaseRef = database.child(rootPathTree)
            val defaultModelPath = "$CHILD_MODELS3D/$CHILD_DEFAULT_MODEL"

            // Check the default properties, if not exist, get it from assets folder
            funx.checkDefaultProperties(this,storageRef, databaseRef,defaultModelPath,"$DEFAULT_MODEL_FILENAME.gltf", CHILD_GLTF_FILE)
            funx.checkDefaultProperties(this,storageRef, databaseRef,defaultModelPath,"$DEFAULT_MODEL_FILENAME.bin", CHILD_BIN_FILE)
            funx.defaultModelListener(storage, databaseRef, defaultModelPath, gltfModelFile, binModelFile, object : DatabaseCallBack{
                override fun onCallBack(gltfFile: File, binFile: File) {

                    /* [Start Configure Sceneform scene] */
                    funx.writeDataFromInternal(this@MainActivity, mGoogleUid, "$DEFAULT_MODEL_FILENAME.gltf", gltfFile.readBytes())
                    funx.writeDataFromInternal(this@MainActivity, mGoogleUid, "$DEFAULT_MODEL_FILENAME.bin", binFile.readBytes())
                    val gltfInternal = File("$filesDir/$mGoogleUid/$DEFAULT_MODEL_FILENAME.gltf")
                    val binInternal = File("$filesDir/$mGoogleUid/$DEFAULT_MODEL_FILENAME.bin")

                    mMessageEditText.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                        override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                            mMessageSendButton.isEnabled = charSequence.toString().trim { it <= ' ' }.isNotEmpty()
                        }
                        override fun afterTextChanged(editable: Editable) {}
                    })
                    var modeBit = ModeEncytion.LOWBIT
                    mHighBitSwitch.setOnCheckedChangeListener{ _, isChecked: Boolean ->
                        modeBit = if(isChecked){
                            ModeEncytion.HIGHBIT
                        }else{
                            ModeEncytion.LOWBIT
                        }
                    }

                    mMessageSendButton.setOnClickListener {
                        message = mMessageEditText.text.toString()
                        val afterCombiner = gltfHandler.hidingMessage(gltfFile, binFile, message, modeBit)
                        afterCombiner?.let {
                            storage.reference.child("$userPathTree/$defaultModelPath/$DEFAULT_MODEL_FILENAME.bin").putBytes(it).addOnCompleteListener{task ->
                                if(task.isSuccessful){
                                    task.result?.metadata?.reference?.downloadUrl?.addOnSuccessListener {url ->
                                        database.child("$userPathTree/binMessageUrl").setValue(url.toString())
                                    }
                                }
                            }
                        }
                        mMessageEditText.text = null
                        mMessageSendButton.isEnabled
                    }

                    // Get info from user
                    database.child(userPathTree).addValueEventListener(object : ValueEventListener{
                        override fun onCancelled(snapshot: DatabaseError) {}
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val dataUser = snapshot.getValue(User::class.java)
                            if(dataUser!=null){
                                try{
                                    if(!dataUser.binMessageUrl.isNullOrEmpty()){
                                        storage.getReferenceFromUrl(dataUser.binMessageUrl.toString()).getFile(binInternal)
                                    }
                                }catch (e: IOException){
                                    e.printStackTrace()
                                }
                            }
                            placeObject(this@MainActivity, Uri.parse(gltfInternal.path), MODEL_SCALE)
                            arFragment.setOnTapArPlaneListener { hitResult, _, _ ->
                                val anchor = hitResult.createAnchor()
                                if(renderModel == null) placeObject(this@MainActivity, Uri.parse(modelLink), MODEL_SCALE)
                                addNodeToScene(anchor, arFragment, renderModel)
                                val originalMessage = gltfHandler.getOriginalMessage(binInternal.readBytes(), binFile.readBytes())
                                Toast.makeText(this@MainActivity, "Message: $originalMessage", Toast.LENGTH_LONG).show()
                            }
                        }
                    })
                    /* [End Configure Sceneform scene] */
                }
            })
            /* [End Configure Firebase database part] */
        }
        /* [End Configure Firebase part] */
    }

    /* [Start initialize menu part] */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.sign_out_menu -> {
                auth.signOut()
                mGoogleSignInClient.signOut()
                mUsername = ANONYMOUS
                startActivity(Intent(this, SignInActivity::class.java))
                finish()
                true
            }else -> super.onOptionsItemSelected(item)
        }
    }
    /* [End initialize menu part] */

    /* [ Start function handle with Sceneform/ARCore] */
    // Initialize the object into scene
    fun placeObject(context: Context, uri: Uri, scale: Float){
        ModelRenderable.builder()
            .setSource(context, RenderableSource.builder().setSource(
                context,
                uri,
                RenderableSource.SourceType.GLTF2)
                .setRecenterMode(RenderableSource.RecenterMode.ROOT)
                .setScale(scale)
                .build()
            )
            .setRegistryId(uri)
            .build().thenAccept { renderModel = it }
            .exceptionally {
                Toast.makeText(context, "Error in fetching $uri", Toast.LENGTH_SHORT).show()
                return@exceptionally null
            }
    }
    // Add the object just initialized into the scene
    private fun addNodeToScene(anchor: Anchor, arFragment: ArFragment, renderable: ModelRenderable?){
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(arFragment.arSceneView.scene)

        // Create a transformable node and add it to the anchor.
        val node = TransformableNode(arFragment.transformationSystem)
        node.setParent(anchorNode)
        node.renderable = renderable
        node.select()
    }
    /* [ End function handle with Sceneform/ARCore] */

    override fun onDestroy() {
        super.onDestroy()
        if(!isChangingConfigurations){
            deleteTempFiles(cacheDir)
        }
    }

    private fun deleteTempFiles(file: File) {
        if(file.isDirectory){
            val files = file.listFiles()
            if(files!=null){
                for(f in files){
                    if(f.isDirectory){
                        deleteTempFiles(f)
                    }else f.delete()
                }
            }
        }
        return file.deleteOnExit()
    }

}
