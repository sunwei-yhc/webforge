package io.github.sunwei.webforge.permission

import android.content.Intent
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import java.util.Arrays

/**
 */
class Action : Parcelable {
    var permissions: ArrayList<String>? = ArrayList()
    var action: Int = 0
    var fromIntention: Int = 0
    var intent: Intent? = null
    var uri: Uri? = null
    var rationaleListener: WebForgeActionFragment.RationaleListener? = null
    var permissionListener: WebForgeActionFragment.PermissionListener? = null
    var chooserListener: WebForgeActionFragment.ChooserListener? = null

    constructor() {}

    private constructor(source: Parcel) {
        permissions = source.createStringArrayList()
        action = source.readInt()
        fromIntention = source.readInt()
        intent = source.readParcelable(Intent::class.java.classLoader)
        uri = source.readParcelable(Uri::class.java.classLoader)
    }

    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeStringList(permissions)
        destination.writeInt(action)
        destination.writeInt(fromIntention)
        destination.writeParcelable(intent, flags)
        destination.writeParcelable(uri, flags)
    }

    override fun describeContents(): Int = 0

    fun setPermissions(permissions: Array<String>) {
        this.permissions = ArrayList(permissions.asList())
    }


    fun setFromIntention(fromIntention: Int): Action {
        this.fromIntention = fromIntention
        return this
    }

    companion object {
        @Transient
        const val ACTION_PERMISSION: Int = 1

        @Transient
        const val ACTION_FILE: Int = 2

        @Transient
        const val ACTION_CAMERA: Int = 3

        @Transient
        const val ACTION_VIDEO: Int = 4
        @JvmStatic
        fun createPermissionsAction(permissions: Array<String>): Action {
            val mAction = Action()
            mAction.action = ACTION_PERMISSION
            mAction.permissions = ArrayList(permissions.asList())
            return mAction
        }

        @JvmField
        val CREATOR = object : Parcelable.Creator<Action> {
            override fun createFromParcel(source: Parcel): Action = Action(source)

            override fun newArray(size: Int): Array<Action?> = arrayOfNulls(size)
        }
    }
}
