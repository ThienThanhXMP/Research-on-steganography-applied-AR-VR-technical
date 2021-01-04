package com.thanhthienxmp.arcore_datahiding_thesis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
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
import com.google.gson.Gson
import com.thanhthienxmp.arcore_datahiding_thesis.model.DefaultModel
import com.thanhthienxmp.arcore_datahiding_thesis.gltf_handler.GLTFHandler
import com.thanhthienxmp.arcore_datahiding_thesis.model.GLTFModel

import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import kotlin.experimental.xor

class MainActivityOld : AppCompatActivity() {

    // Sceneform variables
    private lateinit var arFragment: ArFragment
    private var modelLink = "model.gltf"
    private var renderModel: ModelRenderable? = null

    // Tag/default name
    companion object{
        private const val TAG = "#MainActivity"
        private const val ANONYMOUS = "anonymous"

        // Firebase child
        private const val CHILD_USERS = "users"
        private const val CHILD_ROOT_USER = "rootUser"
        private const val CHILD_MODELS3D = "models3D"
        private const val CHILD_CUBE_MODEL = "cubeModel"
        private const val CHILD_GLTF_FILE = "modelGltfFile"
        private const val CHILD_BIN_FILE = "modelBinFile"
        private const val CHILD_BACKUP_BIN_FILE = "modelBackupBinFile"
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toolbar.title = getString(R.string.app_name)
        setSupportActionBar(toolbar)

        /* [Start Configure Sceneform part] */
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
            mFirebaseUser = auth.currentUser!!
            mUsername = mFirebaseUser.displayName.toString()
            mPhotoUrl = if(mFirebaseUser.photoUrl != null) mFirebaseUser.photoUrl.toString() else ""

            database = FirebaseDatabase.getInstance().reference

            /* [Start Configure Storage part] */
            // storage = Firebase.storage
            storage = Firebase.storage("gs://${getString(R.string.google_storage_bucket)}")
            // Create a storage reference from app
            storageRef = storage.reference.child(CHILD_ROOT_USER).child(CHILD_MODELS3D)
            val databaseRef = database.child(CHILD_ROOT_USER).child(CHILD_MODELS3D).child(CHILD_CUBE_MODEL)

            // Check the default properties, if not exist, get it from assets folder
            checkDefaultProperties(storageRef, databaseRef,"cube.gltf", CHILD_GLTF_FILE)
            checkDefaultProperties(storageRef, databaseRef, "cube.bin", CHILD_BIN_FILE)

            storageRef.child("$CHILD_CUBE_MODEL/cube.gltf").downloadUrl.addOnSuccessListener {
                modelLink = it.path.toString()
            }.addOnFailureListener {
                modelLink = "model.gltf"
            }

            try {
                val file: File = createTempFile("cube","gltf")
                storageRef.child("$CHILD_CUBE_MODEL/cube.gltf").getFile(file).addOnSuccessListener {
                    val binFile: File = createTempFile("cube","bin")
                    storageRef.child("$CHILD_CUBE_MODEL/cube.bin").getFile(binFile).addOnSuccessListener {
                        Log.i(TAG, "Test file bin path: {${binFile.path}}")
                        Log.i(TAG, "Test file path: {${file.path}}")
                    }
                }
            }catch (e: IOException){
                e.printStackTrace()
            }

            val defModelListener = object : ValueEventListener{
                override fun onCancelled(dataError: DatabaseError) {
                    Log.e(TAG, "load model onCancelled", dataError.toException())
                }

                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val defaultModel = dataSnapshot.getValue(DefaultModel::class.java)
                    if(defaultModel != null){
                        modelLink = defaultModel.modelGltfFile.toString()
                    }
                }
            }
            databaseRef.addValueEventListener(defModelListener)
            /* [End Configure Storage part] */
        }
        /* [End Configure Firebase part] */
        arFragment.setOnTapArPlaneListener { hitResult, _, _ ->
            val anchor = hitResult.createAnchor()
            placeObject(arFragment, modelLink.toUri(), anchor)
        }

        /* [End Configure Sceneform part] */

        /* [Start Handled GLTF file as JSON file] */
        // Can't write the data into the file in assets folder
        // Get Object from the file
        val jsonFileString = getJsonDataFromAsset(applicationContext, modelLink)
        // val jsonFileString = mUriDefGltfFile.toFile().bufferedReader().use { it.readLine() }
        // the variables below save the object and info of the model's gltf file
        val mGLTFHandled = Gson().fromJson<GLTFModel.GLTFComponents>(jsonFileString, GLTFModel.GLTFComponents::class.java)

        // Binary file
        val binaryModel = this.assets.open("cube.bin").readBytes()
        // val binaryModel = mUriDefGltfFile!!.toFile().readBytes()
        GLTFHandler().printBinaryMatrix(binaryModel)

        // Display bin file in hex string
        val hexString = StringBuffer()
        binaryModel.forEach { hexString.append(GLTFHandler().intToHexChar(it))}
        // Log.i("#GLTF byte", hexString2.toString())
        Log.i("#GLTF byte length", binaryModel.size.toString())
        Log.i("#GLTF test convert", binaryModel[binaryModel.size-1].toString())
        Log.i("#GLTF test convert", GLTFHandler().hexSCharToInt("3f").toString())

        // Message
        val message = "Chào bạn, làm quen nha!"
        val hexString2: String = GLTFHandler().convertStrToHexStr(message)
        Log.i("#GLTF - Convert String to HexString", hexString2)
        Log.i("#GLTF - Convert HexString to Original", GLTFHandler().convertHexStrToOriginal(hexString2))
        val afterCombine = combineMessageIntoBinFile(message, binaryModel)
        GLTFHandler().printBinaryMatrix(afterCombine)

        // Get the secret file from the function below
        Log.i("#GLTF get the secret message", getOriginalMessage(binaryModel,afterCombine))
    }

    // Combine secret message into the model's bin file
    private fun combineMessageIntoBinFile(message: String, binFile: ByteArray): ByteArray{
        val tempBinFile = binFile.copyOf()
        val byteArrayMessage = GLTFHandler().convertStrToByteArray(message)
        val binFileSize = tempBinFile.size
        for (index in byteArrayMessage.indices){
            tempBinFile[binFileSize-4-index*4] = tempBinFile[binFileSize-4-index*4] xor byteArrayMessage[index]
        }
        return tempBinFile
    }

    // Get secret message from the original bin file and combined bin fle
    private fun getOriginalMessage(binFileOriginal: ByteArray, binFileEncrypted: ByteArray): String{
        val byteArrayMessage: ArrayList<Byte> = ArrayList()
        if(binFileOriginal.size == binFileEncrypted.size){
            binFileOriginal.forEachIndexed { index, byte ->
                if(binFileEncrypted[index] != byte){
                    byteArrayMessage.add(binFileEncrypted[index] xor byte)
                }
            }
        }
        return GLTFHandler().convertByteArrayToStr(byteArrayMessage.toByteArray().reversedArray())
    }

    private fun getJsonDataFromAsset(context: Context, fileName: String): String?{
        val jsonString: String
        try{
            jsonString = context.assets.open(fileName).bufferedReader().use{it.readText()}
        }catch (e: IOException){
            Log.d("Reading file warming: ", "Can't read file", e)
            return null
        }
        return jsonString
    }
    /* [End Handled GLTF file as JSON file] */

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

    /* [Start Check default properties part] */
    // Check the default model have been existed yet
    private fun checkDefaultProperties(defaultModelRef: StorageReference, databaseRef: DatabaseReference, modelLink: String, mode: String){
        defaultModelRef.downloadUrl.addOnCompleteListener{first ->
            if(!first.isSuccessful){
                val file = Uri.fromFile(getFileFromAssets(this, modelLink))
                defaultModelRef.child("$CHILD_CUBE_MODEL/${file.lastPathSegment}").putFile(file).addOnCompleteListener{second ->
                    if(!second.isSuccessful) Log.e(TAG, "Can't upload file!")
                    else second.result?.metadata?.reference?.downloadUrl?.addOnSuccessListener {
                        databaseRef.child(mode).setValue(it.toString())
                    }
                }
            }else databaseRef.child(mode).setValue(first.result.toString())
        }
    }
    /* [End Check default properties part] */

    /* [Start sceneform handle part] */
    private fun placeObject(arFragment: ArFragment, model: Uri, anchor: Anchor){
        ModelRenderable.builder()
            .setSource(arFragment.context, RenderableSource.builder().setSource(
                arFragment.context,
                model,
                RenderableSource.SourceType.GLTF2).build())
            .build().thenAccept { addNodeToScene(anchor, arFragment, it) }
            .exceptionally {
                Toast.makeText(this@MainActivityOld, "Error in fetching $model", Toast.LENGTH_SHORT).show()
                return@exceptionally null
        }
    }

    private fun addNodeToScene(anchor: Anchor, arFragment: ArFragment, renderable: ModelRenderable){
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(arFragment.arSceneView.scene)

        // Create a transformable node and add it to the anchor.
        val node = TransformableNode(arFragment.transformationSystem)
        node.setParent(anchorNode)
        node.renderable = renderable
        node.select()
    }
    /* [End sceneform handle part] */

    private fun getFileFromAssets(context: Context, fileName: String): File = File(context.cacheDir, fileName)
        .also {file ->
            if (!file.exists()) {
                file.outputStream().use { cache ->
                    context.assets.open(fileName).use {
                        it.copyTo(cache)
                    }
                }
            }
        }

    fun hidingMessage(gltfFile: File?, binFile: File?, message: String){
        /* [Start Handled GLTF file as JSON file] */
        // Can't write the data into the file in assets folder
        // Get Object from the file
        if(gltfFile != null){
            val jsonFileString = gltfFile.bufferedReader().use { it.readText() }
            // val jsonFileString = mUriDefGltfFile.toFile().bufferedReader().use { it.readLine() }
            // the variables below save the object a2nd info of the model's gltf file
            Log.i("#GLTF - test String", jsonFileString)
            val mGLTFHandled = Gson().fromJson<GLTFModel.GLTFComponents>(jsonFileString, GLTFModel.GLTFComponents::class.java)
            if(binFile != null){
                // Binary file
                val binaryModel = binFile.readBytes()
                // val binaryModel = this.assets.open("cube.bin").readBytes()
                // printBinaryMatrix(binaryModel)

                // Display bin file in hex string
                // val hexString = StringBuffer()
                // binaryModel.forEach { hexString.append(gltfConverter.intToHexChar(it))}
                // Log.i("#GLTF byte", hexString2.toString())
                // Log.i("#GLTF byte length", binaryModel.size.toString())
                // Log.i("#GLTF test convert", binaryModel[binaryModel.size-1].toString())
                // Log.i("#GLTF test convert", gltfConverter.hexSCharToInt("3f").toString())

                // Message
                // val message = "Chào bạn, làm quen nha!"
                // val hexString: String = convertStrToHexStr(message)
                // Log.i("#GLTF - Convert String to HexString", hexString)
                // Log.i("#GLTF - Convert HexString to Original", gltfConverter.convertHexStrToOriginal(hexString))
                val afterCombine = combineMessageIntoBinFile(message, binaryModel)
                // printBinaryMatrix(afterCombine)

                // Get the secret file from the function below
                Log.i("#GLTF get the secret message", getOriginalMessage(binaryModel,afterCombine))
            }
        }
        /* [End Handled GLTF file as JSON file] */
    }
}

/* [Start Trash part ] */
//            // Add listener event to listen the changed data
//            val defModelListener = object : ValueEventListener{
//                override fun onCancelled(dataError: DatabaseError) {
//                    Log.e(TAG, "load model onCancelled", dataError.toException())
//                }
//                override fun onDataChange(dataSnapshot: DataSnapshot) {
//                    val defaultModel = dataSnapshot.getValue(DefaultModel::class.java)
//                    if(defaultModel != null){
//                        // Try to get the model file and set the onClickListener()
//                        try {
//                            storage.getReferenceFromUrl(defaultModel.modelGltfFile.toString()).getFile(gltfModelFile).addOnSuccessListener {
//                                storage.getReferenceFromUrl(defaultModel.modelBinFile.toString()).getFile(binModelFile).addOnSuccessListener {
//                                    placeObject(this@MainActivity, Uri.parse(gltfModelFile.path), 0.3f)
//                                    gltfHandler.hidingMessage(gltfModelFile, binModelFile, "Chào bạn, làm quen nha!")
//                                }
//                            }
//                        }catch (e: IOException){
//                            e.printStackTrace()
//                        }
//                    }
//                }
//            }
//            databaseRef.addValueEventListener(defModelListener)

//Log.i("Data showing: ", jsonFileString)

/* Init Gson */
//        val collectionType = object : TypeToken<Collection<GLTFHandled.GLTFComponents>>(){}.type
//        val mGLTFComponents: Collection<GLTFHandled.GLTFComponents> = Gson().fromJson(jsonFileString, collectionType)
//        for(s in mGLTFComponents){
//            println(s.javaClass.classes.size)
//        }
//        mGLTFHandled.bufferViews.forEachIndexed { index, accessorsComponent ->(
//            Log.d("buffer $index", ">Item $index: ${accessorsComponent.buffer} \n" +
//                    "${accessorsComponent.byteLength} \n" +
//                    "${accessorsComponent.byteOffset} \n" +
//                    "${accessorsComponent.name} \n" +
//                    "${accessorsComponent.target}")
//        )}
//        Log.d("TESTING_VALUE", mGLTFHandled.bufferViews[0].name)

/* Handled *.bin file format for GLTF file */

// val fileBinaryModel = getFileFromAssets(this, "cube.bin").absolutePath

// Log.i("Get path", this.filesDir.absolutePath.toString())
/* [End Trash part ] */