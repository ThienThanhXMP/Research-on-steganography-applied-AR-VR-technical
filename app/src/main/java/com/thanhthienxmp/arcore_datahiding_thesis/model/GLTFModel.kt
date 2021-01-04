package com.thanhthienxmp.arcore_datahiding_thesis.model

class GLTFModel {
    data class GLTFComponents(
        // Name must be compare with json components file
        var accessors: List<AccessorsComponent>,
        var bufferViews: List<BufferViewsComponent>,
        var buffers: List<BuffersComponent>
    )

    data class AccessorsComponent(
        var bufferView: Int= 0,
        var byteOffset: Int = 0,
        var count: Int = 0,
        var max: List<Double> = emptyList(),
        var min: List<Double> = emptyList(),
        var name: String = "",
        var type: String = ""
    )

    data class BufferViewsComponent(
        var buffer: Int = 0,
        var byteLength: Int = 0,
        var byteOffset: Int = 0,
        var name: String = "",
        var target: Int = 0
    )

    data class BuffersComponent(
        var byteLength: Int = 0,
        var name: String = "",
        var uri: String = ""
    )
}

