package com.vmloft.develop.app.template.request.bean

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize

/**
 * Create by lzan13 on 2020/7/30 16:04
 * 描述：角色对象数据 Bean
 */
@Parcelize
@Entity
data class Version(
    @PrimaryKey
    @SerializedName("_id")
    val id: String = "",
    val platform: String = "android",
    val title: String = "暂无更新",
    val desc: String = "暂无更新",
    val url: String = "",
    val versionCode: Int = 1,
    val versionName: String = "0.0.1",
    val force: Boolean = false,
) : Parcelable {
}