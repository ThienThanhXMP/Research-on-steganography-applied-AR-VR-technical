package com.thanhthienxmp.arcore_datahiding_thesis

import java.io.File

interface DatabaseCallBack {
    fun onCallBack(gltfFile: File, binFile: File)
}