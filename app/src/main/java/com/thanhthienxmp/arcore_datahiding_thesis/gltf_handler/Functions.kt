package com.thanhthienxmp.arcore_datahiding_thesis.gltf_handler

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.thanhthienxmp.arcore_datahiding_thesis.DatabaseCallBack
import com.thanhthienxmp.arcore_datahiding_thesis.model.DefaultModel
import java.io.*

class Functions{
    /* [Start function get and handle file in assets folder] */
    // Read data from assets and return the string
    fun readDataFromAsset(context: Context, fileName: String): String?{
        val jsonString: String
        try{
            jsonString = context.assets.open(fileName).bufferedReader().use{it.readText()}
        }catch (e: IOException){
            Log.d("Reading file warming: ", "Can't read file", e)
            return null
        }
        return jsonString
    }
    // Get files from assets and save into cache
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

    // Check the default model have been existed yet
    fun checkDefaultProperties(context: Context, storageRef: StorageReference, databaseRef: DatabaseReference, path: String, modelLink: String, mode: String){
        storageRef.child("$path/$modelLink").downloadUrl.addOnCompleteListener{first ->
            if(!first.isSuccessful){
                val file = Uri.fromFile(getFileFromAssets(context, modelLink))
                storageRef.child("$path/${file.lastPathSegment}").putFile(file).addOnCompleteListener{ second ->
                    if(second.isSuccessful)second.result?.metadata?.reference?.downloadUrl?.addOnSuccessListener {
                        databaseRef.child("$path/$mode").setValue(it.toString())
                    }
                }
            }else databaseRef.child(mode).addListenerForSingleValueEvent(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    val url = first.result.toString()
                    if (snapshot.value != url || !snapshot.exists()){
                        databaseRef.child("$path/$mode").setValue(url)
                    }
                }
                override fun onCancelled(snapshot: DatabaseError) {}
            })
        }
    }

    fun defaultModelListener(storage: FirebaseStorage, databaseRef: DatabaseReference, path: String, gltfFile: File, binFile: File, callBack: DatabaseCallBack){
        // Add listener event to listen the changed data
        val defModelListener = object : ValueEventListener {
            override fun onCancelled(dataError: DatabaseError) {}
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val defaultModel = dataSnapshot.getValue(DefaultModel::class.java)
                if(defaultModel != null){
                    // Try to get the model file and set the onClickListener()
                    try {
                        storage.getReferenceFromUrl(defaultModel.modelGltfFile.toString()).getFile(gltfFile).addOnSuccessListener {
                            storage.getReferenceFromUrl(defaultModel.modelBinFile.toString()).getFile(binFile).addOnSuccessListener {
                                callBack.onCallBack(gltfFile, binFile)
                            }
                        }
                    }catch (e: IOException){
                        e.printStackTrace()
                    }
                }
            }
        }
        databaseRef.child(path).addValueEventListener(defModelListener)
    }

    fun writeDataFromInternal(context: Context, folderPath: String, fileName: String, data: ByteArray){
        try {
            val folder = context.filesDir.absolutePath + File.separator + folderPath
            val subFolder = File(folder)
            if(!subFolder.exists()) subFolder.mkdir()
            val fos = FileOutputStream(File(subFolder,fileName))
            fos.write(data)
            fos.close()
        }catch (e: IOException){
            e.printStackTrace()
        }catch (e: FileNotFoundException){
            e.printStackTrace()
        }
    }
}