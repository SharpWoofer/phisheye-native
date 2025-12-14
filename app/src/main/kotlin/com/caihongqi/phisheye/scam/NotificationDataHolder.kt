package com.caihongqi.phisheye.phisheye

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

// This is our Singleton object
object NotificationDataHolder {

    // The private, mutable LiveData that we will update from our service
    private val _notificationData = MutableLiveData<AnalyzedNotification>()

    // The public, immutable LiveData that our MainActivity will observe for changes
    val notificationData: LiveData<AnalyzedNotification> = _notificationData

    private val _serviceActive = MutableLiveData(false)
    val serviceActive: LiveData<Boolean> = _serviceActive

    // This function will be called by the NotificationListenerService
    // to post a new update.
    fun onNewNotification(result: AnalyzedNotification) {
        _notificationData.postValue(result)
    }

    fun updateServiceState(isActive: Boolean) {
        _serviceActive.postValue(isActive)
    }
}
