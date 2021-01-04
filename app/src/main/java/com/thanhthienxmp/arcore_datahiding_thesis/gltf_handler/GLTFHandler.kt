package com.thanhthienxmp.arcore_datahiding_thesis.gltf_handler

import android.util.Log
import com.google.gson.Gson
import com.thanhthienxmp.arcore_datahiding_thesis.ModeEncytion
import com.thanhthienxmp.arcore_datahiding_thesis.model.GLTFModel
import java.io.File
import java.nio.charset.Charset
import kotlin.experimental.xor

class GLTFHandler{

    /* Root converting  (Non-support UTF-8)*/
    fun intToHexChar(byte: Any):String = String.format("%02x", byte)
    fun hexSCharToInt(hexString: String): Int = Integer.valueOf(hexString,16)
    // Convert char into hex
    fun convertCharToHex(value: Char) = Integer.toHexString(value.toInt())
    // Convert char into bin
    fun convertCharToBin(value: Char) = Integer.toBinaryString(value.toInt())

    /* Showing part */
    fun printBinaryMatrix(byteArray: ByteArray, widthSize: Int = 16){
        val stringBuffer = StringBuffer()
        for (i:Int in byteArray.indices){
            when{
                i%widthSize==widthSize-1 -> stringBuffer.append(intToHexChar(byteArray[i])+"\n")
                i%2==0 -> stringBuffer.append(intToHexChar(byteArray[i]))
                i%2!=0 -> stringBuffer.append(intToHexChar(byteArray[i])+" ")
            }
        }
        Log.i("#GLTF - Binary Matrix", "\n"+stringBuffer.toString())
    }

    /* Convert function, non-support UFT-8 */
    // Convert String to Custom mode, none support UTF-8
    fun convertStrToHexOrBin(text: String, mode: Int = 0): String{
        val plainText = StringBuilder()
        for (i in text) {
            when(mode){
                0 -> plainText.append(convertCharToHex(i))
                1 -> plainText.append(convertCharToBin(i))
            }
        }
        return plainText.toString()
    }

    // Convert support UTF-8
    fun convertStrToHexStr(message: String) = convertByteArrayToHexStr(convertStrToByteArray(message))
    fun convertHexStrToOriginal(hexString: String) = convertByteArrayToStr(convertHexStrToByteArray(hexString))

    // HexString to ByteArray
    fun convertHexStrToByteArray(text: String): ByteArray {
        val byteArray = ByteArray(text.length / 2)
        for (i in  byteArray.indices) {
            val tempByte = hexSCharToInt(text.substring(i * 2, i * 2 + 1)) * 16 + hexSCharToInt(text.substring(i * 2 + 1, i * 2 + 2))
            byteArray[i] = tempByte.toByte()
        }
        return byteArray
    }
    // ByteArray to HexString
    fun convertByteArrayToHexStr(byteArray: ByteArray): String{
        val stringBuffer = StringBuffer()
        byteArray.forEachIndexed { _, byte -> run {stringBuffer.append(intToHexChar(byte))} }
        return stringBuffer.toString()
    }
    // String To ByteArray
    fun convertStrToByteArray(message: String, charset: String = "UTF-8"): ByteArray = message.toByteArray(Charset.forName(charset))
    // ByteArray To String
    fun convertByteArrayToStr(byteArray: ByteArray, charset: String = "UTF-8") = String(byteArray, Charset.forName(charset))

    /* [Start handle with bin file] */
    // Combine secret message into the model's bin file
    private fun combinerMessageIntoBinFile(message: String, binFile: ByteArray, mode: ModeEncytion = ModeEncytion.LOWBIT): ByteArray{
        val tempBinFile = binFile.copyOf()
        val byteArrayMessage = convertStrToByteArray(message)
        val binFileSize = tempBinFile.size
        val bit = if(mode == ModeEncytion.HIGHBIT) 1 else 4
        for (index in byteArrayMessage.indices){
            tempBinFile[binFileSize-bit-index*bit] = tempBinFile[binFileSize-bit-index*bit] xor byteArrayMessage[index]
        }
        return tempBinFile
    }

    // Get secret message from the original bin file and combined bin fle
    fun getOriginalMessage(binFileOriginal: ByteArray, binFileEncrypted: ByteArray): String{
        val byteArrayMessage: ArrayList<Byte> = ArrayList()
        if(binFileOriginal.size == binFileEncrypted.size){
            binFileOriginal.forEachIndexed { index, byte ->
                if(binFileEncrypted[index] != byte){
                    byteArrayMessage.add(binFileEncrypted[index] xor byte)
                }
            }
        }
        return convertByteArrayToStr(byteArrayMessage.toByteArray().reversedArray())
    }
    /* [End handle with bin file] */

    fun hidingMessage(gltfFile: File?, binFile: File?, message: String, mode: ModeEncytion): ByteArray?{
        var afterCombine: ByteArray? = null
        /* [Start Handled GLTF file as JSON file] */
        // Can't write the data into the file in assets folder
        // Get Object from the file
        if(gltfFile != null){
            val jsonFileString = gltfFile.bufferedReader().use { it.readText() }
            val mGLTFHandled = Gson().fromJson<GLTFModel.GLTFComponents>(jsonFileString, GLTFModel.GLTFComponents::class.java)
            if(binFile != null){
                // Combine message into bin file
                afterCombine = combinerMessageIntoBinFile(message, binFile.readBytes(), mode)
                // Get the secret file from the function below
                // Log.i("#GLTF get the secret message", getOriginalMessage(binaryModel,afterCombine))
            }
        }
        /* [End Handled GLTF file as JSON file] */
        return afterCombine
    }
}