package com.caihongqi.phisheye.interfaces

import androidx.room.Dao
import androidx.room.Query
import com.caihongqi.phisheye.models.MessageAttachment

@Dao
interface MessageAttachmentsDao {
    @Query("SELECT * FROM message_attachments")
    fun getAll(): List<MessageAttachment>
}
