package com.thanhthienxmp.arcore_datahiding_thesis.model

import java.io.Serializable

class User: Serializable {
    public var id: String? = ""
    public var name: String? = ""
    public var account: String? = ""
    public var photo: String? = ""
    public var privateKey: String? = ""
    public var publicKey: String? = ""
    public var binMessageUrl: String? = ""

    constructor()

    constructor(id: String?, name: String?, account: String?, photo: String?){
        this.id = id
        this.name = name
        this.account = account
        this.photo = photo
    }
}