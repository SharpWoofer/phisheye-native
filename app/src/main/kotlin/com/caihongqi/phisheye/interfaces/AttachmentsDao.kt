package com.caihongqi.phisheye.interfaces

import androidx.room.Dao
import androidx.room.Query
import com.caihongqi.phisheye.models.Attachment

@Dao
interface AttachmentsDao {
    @Query("SELECT * FROM attachments")
    fun getAll(): List<Attachment>
}
