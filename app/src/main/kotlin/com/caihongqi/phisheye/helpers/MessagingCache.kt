package com.caihongqi.phisheye.helpers

import android.util.LruCache
import org.fossify.commons.models.SimpleContact
import com.caihongqi.phisheye.models.NamePhoto

private const val CACHE_SIZE = 512

object MessagingCache {
    val namePhoto = LruCache<String, NamePhoto>(CACHE_SIZE)
    val participantsCache = LruCache<Long, ArrayList<SimpleContact>>(CACHE_SIZE)
}
