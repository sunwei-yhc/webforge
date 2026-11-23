package io.github.sunwei.webforge.filechooser

import android.os.Parcel
import android.os.Parcelable

/**
 */
open class FileParcel : Parcelable {
    open var id: Int
    open var contentPath: String?
    open var fileBase64: String?

    protected constructor(`in`: Parcel) {
        this.id = `in`.readInt()
        this.contentPath = `in`.readString()
        this.fileBase64 = `in`.readString()
    }

    constructor(id: Int, contentPath: String?, fileBase64: String?) {
        this.id = id
        this.contentPath = contentPath
        this.fileBase64 = fileBase64
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(this.id)
        dest.writeString(this.contentPath)
        dest.writeString(this.fileBase64)
    }

    override fun toString(): String {
        return "FileParcel{" +
                "mId=" + this.id +
                ", mContentPath='" + this.contentPath + '\'' +
                ", mFileBase64='" + this.fileBase64 + '\'' +
                '}'
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<FileParcel?> = object : Parcelable.Creator<FileParcel?> {
            override fun createFromParcel(`in`: Parcel): FileParcel {
                return FileParcel(`in`)
            }

            override fun newArray(size: Int): Array<FileParcel?> {
                return arrayOfNulls<FileParcel>(size)
            }
        }
    }
}
