package org.fossify.messages.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import org.fossify.commons.extensions.addBlockedNumber
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkAppSideloading
import org.fossify.commons.extensions.checkWhatsNew
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.helpers.LICENSE_EVENT_BUS
import org.fossify.commons.helpers.LICENSE_INDICATOR_FAST_SCROLL
import org.fossify.commons.helpers.LICENSE_SMS_MMS
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.SHORT_ANIMATION_DURATION
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.Release
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.activities.ArchivedConversationsActivity
import org.fossify.messages.activities.NewConversationActivity
import org.fossify.messages.activities.RecycleBinConversationsActivity
import org.fossify.messages.activities.SettingsActivity
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.activities.ThreadActivity
import org.fossify.messages.adapters.ConversationsAdapter
import org.fossify.messages.adapters.SearchResultsAdapter
import org.fossify.messages.databinding.FragmentMessagesBinding
import org.fossify.messages.extensions.checkAndDeleteOldRecycleBinMessages
import org.fossify.messages.extensions.clearAllMessagesIfNeeded
import org.fossify.messages.extensions.clearExpiredScheduledMessages
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.deleteConversation
import org.fossify.messages.extensions.dialNumber
import org.fossify.messages.extensions.getConversations
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.insertOrUpdateConversation
import org.fossify.messages.extensions.launchConversationDetails
import org.fossify.messages.extensions.markThreadMessagesRead
import org.fossify.messages.extensions.markThreadMessagesUnread
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.renameConversation
import org.fossify.messages.extensions.updateConversationArchivedStatus
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.interfaces.ConversationInteractionListener
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.fossify.messages.models.SearchResult
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MessagesFragment : Fragment(), ConversationInteractionListener {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private var storedTextColor = 0
    private var storedFontSize = 0
    private var bus: EventBus? = null
    
    private var searchResultsAdapter: SearchResultsAdapter? = null
    private var searchQuery = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        
        binding.conversationsFab.setOnClickListener {
            launchNewConversation()
        }
        
        binding.noConversationsPlaceholder2.setOnClickListener {
            launchNewConversation()
        }
        
        bus = EventBus.getDefault()
        try {
            bus!!.register(this)
        } catch (_: Exception) {
        }
    }
    
    private fun setupToolbar() {
        binding.mainToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.settings -> {
                    launchSettings()
                    true
                }
                R.id.show_recycle_bin -> {
                    launchRecycleBin()
                    true
                }
                R.id.show_archived -> {
                    launchArchivedConversations()
                    true
                }
                R.id.about -> {
                    launchAbout()
                    true
                }
                else -> false
            }
        }

        val searchItem = binding.mainToolbar.menu.findItem(R.id.search)
        val searchView = searchItem.actionView as? SearchView
        
        searchView?.apply {
            queryHint = getString(R.string.search)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchQuery = query ?: ""
                    searchMessages(searchQuery)
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    searchQuery = newText ?: ""
                    searchMessages(searchQuery)
                    return true
                }
            })
            
            setOnCloseListener {
                false
            }
        }
        
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                binding.searchHolder.beVisible()
                binding.searchHolder.animate().alpha(1f).duration = SHORT_ANIMATION_DURATION
                binding.conversationsFab.beGone()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                binding.searchHolder.animate().alpha(0f).duration = SHORT_ANIMATION_DURATION
                binding.searchHolder.beGone()
                binding.conversationsFab.beVisible()
                return true
            }
        })
    }
    
    private fun searchMessages(text: String) {
        val minChars = 2
        if (text.length < minChars) {
            binding.searchPlaceholder.beVisible()
            binding.searchPlaceholder2.beVisible()
            binding.searchResultsList.beGone()
            return
        }

        binding.searchPlaceholder.beGone()
        binding.searchPlaceholder2.beGone()
        binding.searchResultsList.beVisible()

        ensureBackgroundThread {
            val context = context ?: return@ensureBackgroundThread
            val messages = try {
                 context.messagesDB.getMessagesWithText("%$text%")
            } catch (e: Exception) {
                ArrayList()
            }
            
            val searchResults = ArrayList<SearchResult>()
            val threadMap = HashMap<Long, Conversation>()
            
            messages.forEach { msg ->
                val threadId = msg.threadId
                var conversation = threadMap[threadId]
                if (conversation == null) {
                    conversation = context.conversationsDB.getConversationWithThreadId(threadId)
                    if (conversation != null) {
                        threadMap[threadId] = conversation
                    }
                }
                
                if (conversation != null) {
                    val date = msg.date.formatDateOrTime(context, true, true)
                    val title = conversation.title
                    val snippet = msg.body
                    val photoUri = conversation.photoUri
                    
                    searchResults.add(SearchResult(msg.id, title, snippet, date, threadId, photoUri))
                }
            }

            activity?.runOnUiThread {
                 getOrCreateSearchResultsAdapter().updateItems(searchResults, text)
            }
        }
    }
    
    private fun getOrCreateSearchResultsAdapter(): SearchResultsAdapter {
        var currAdapter = searchResultsAdapter
        if (currAdapter == null) {
            currAdapter = SearchResultsAdapter(
                activity = requireActivity() as SimpleActivity,
                searchResults = ArrayList(),
                recyclerView = binding.searchResultsList,
                highlightText = searchQuery
            ) {
                 handleSearchResultClick(it)
            }
            binding.searchResultsList.adapter = currAdapter
            searchResultsAdapter = currAdapter
        }
        return currAdapter as SearchResultsAdapter
    }
    
    private fun handleSearchResultClick(item: Any) {
         if (item is SearchResult) {
             launchConversationDetails(item.threadId)
         }
    }

    override fun onResume() {
        super.onResume()
        
        getOrCreateConversationsAdapter().apply {
            val properTextColor = requireContext().getProperTextColor()
            if (storedTextColor != properTextColor) {
                updateTextColor(properTextColor)
                storedTextColor = properTextColor
            }

            if (storedFontSize != requireContext().config.fontSize) {
                updateFontSize()
                storedFontSize = requireContext().config.fontSize
            }

            updateDrafts()
        }

        binding.searchHolder.setBackgroundColor(requireContext().getProperBackgroundColor())
        
        val properPrimaryColor = requireContext().getProperPrimaryColor()
        binding.mainToolbar.setBackgroundColor(properPrimaryColor)
        
        binding.noConversationsPlaceholder2.setTextColor(properPrimaryColor)
        binding.noConversationsPlaceholder2.underlineText()
        binding.conversationsFastscroller.updateColors(properPrimaryColor)
        binding.conversationsProgressBar.setIndicatorColor(properPrimaryColor)
        binding.conversationsProgressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)
        
        initMessenger()
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bus?.unregister(this)
        _binding = null
    }
    
    override fun getContext(): Context = super.getContext() ?: requireContext()
    
    override fun launchConversationDetails(threadId: Long) {
        (activity as? SimpleActivity)?.launchConversationDetails(threadId)
    }

    override fun launchNewConversation() {
        requireActivity().hideKeyboard()
        Intent(requireContext(), NewConversationActivity::class.java).apply {
            startActivity(this)
        }
    }

    override fun dialNumber(phoneNumber: String, callback: () -> Unit) {
        (activity as? SimpleActivity)?.dialNumber(phoneNumber, callback)
    }

    override fun copyToClipboard(text: String) {
        (activity as? SimpleActivity)?.copyToClipboard(text)
    }

    override fun addBlockedNumber(number: String) {
        (activity as? SimpleActivity)?.addBlockedNumber(number)
    }

    override fun deleteConversation(threadId: Long) {
        (activity as? SimpleActivity)?.deleteConversation(threadId)
    }

    override fun archiveConversation(threadId: Long) {
        (activity as? SimpleActivity)?.updateConversationArchivedStatus(threadId, true)
        (activity as? SimpleActivity)?.notificationManager?.cancel(threadId.hashCode())
    }

    override fun renameConversation(conversation: Conversation, newTitle: String): Conversation {
        return (activity as? SimpleActivity)?.renameConversation(conversation, newTitle) ?: conversation
    }

    override fun markThreadMessagesRead(threadId: Long) {
        (activity as? SimpleActivity)?.markThreadMessagesRead(threadId)
    }

    override fun markThreadMessagesUnread(threadId: Long) {
        (activity as? SimpleActivity)?.markThreadMessagesUnread(threadId)
    }

    override fun finishActionModeListener() {
        getOrCreateConversationsAdapter().finishActMode()
    }

    override fun refreshConversationsUI() {
        initMessenger()
    }

    override fun getMyContactsCursor(favoritesOnly: Boolean, withPhoneNumbersOnly: Boolean): Cursor? {
        return (activity as? SimpleActivity)?.getMyContactsCursor(favoritesOnly, withPhoneNumbersOnly)
    }

    private fun storeStateVariables() {
        storedTextColor = requireContext().getProperTextColor()
        storedFontSize = requireContext().config.fontSize
    }

    private fun initMessenger() {
        requireActivity().checkAndDeleteOldRecycleBinMessages()
        requireActivity().clearAllMessagesIfNeeded {
            getCachedConversations()
        }

        binding.noConversationsPlaceholder2.setOnClickListener {
            launchNewConversation()
        }
    }

    private fun getCachedConversations() {
        ensureBackgroundThread {
            val context = context ?: return@ensureBackgroundThread
            val conversations = try {
                context.conversationsDB.getNonArchived().toMutableList() as ArrayList<Conversation>
            } catch (_: Exception) {
                ArrayList()
            }

            val archived = try {
                context.conversationsDB.getAllArchived()
            } catch (_: Exception) {
                listOf()
            }

            activity?.runOnUiThread {
                setupConversations(conversations, cached = true)
                getNewConversations(
                    (conversations + archived).toMutableList() as ArrayList<Conversation>
                )
            }
            conversations.forEach {
                context.clearExpiredScheduledMessages(it.threadId)
            }
        }
    }

    private fun getNewConversations(cachedConversations: ArrayList<Conversation>) {
        val context = context ?: return
        val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(context, privateCursor)
            val conversations = context.getConversations(privateContacts = privateContacts)

            conversations.forEach { clonedConversation ->
                val threadIds = cachedConversations.map { it.threadId }
                if (!threadIds.contains(clonedConversation.threadId)) {
                    context.conversationsDB.insertOrUpdate(clonedConversation)
                    cachedConversations.add(clonedConversation)
                }
            }

            cachedConversations.forEach { cachedConversation ->
                val threadId = cachedConversation.threadId

                val isTemporaryThread = cachedConversation.isScheduled
                val isConversationDeleted = !conversations.map { it.threadId }.contains(threadId)
                if (isConversationDeleted && !isTemporaryThread) {
                    context.conversationsDB.deleteThreadId(threadId)
                }

                val newConversation =
                    conversations.find { it.phoneNumber == cachedConversation.phoneNumber }
                if (isTemporaryThread && newConversation != null) {
                    context.conversationsDB.deleteThreadId(threadId)
                    context.messagesDB.getScheduledThreadMessages(threadId)
                        .forEach { message ->
                            context.messagesDB.insertOrUpdate(
                                message.copy(threadId = newConversation.threadId)
                            )
                        }
                    context.insertOrUpdateConversation(newConversation, cachedConversation)
                }
            }

            conversations.forEach { newConv ->
                val cachedConv = cachedConversations.find { it.threadId == newConv.threadId }
                if (cachedConv != null && newConv != cachedConv) {
                    context.insertOrUpdateConversation(newConv, cachedConv)
                } else if (cachedConv == null) {
                    context.conversationsDB.insertOrUpdate(newConv)
                }
            }

            cachedConversations.filter { cachedConv ->
                !conversations.any { it.threadId == cachedConv.threadId }
            }.forEach { deletedConv ->
                context.conversationsDB.deleteThreadId(deletedConv.threadId)
            }

            val allConversations = context.conversationsDB.getNonArchived() as ArrayList<Conversation>
            activity?.runOnUiThread {
                setupConversations(allConversations)
            }

            if (context.config.appRunCount == 1) {
                conversations.map { it.threadId }.forEach { threadId ->
                    val messages = context.getMessages(threadId, includeScheduledMessages = false)
                    messages.chunked(30).forEach { currentMessages ->
                        context.messagesDB.insertMessages(*currentMessages.toTypedArray())
                    }
                }
            }
        }
    }

    private fun getOrCreateConversationsAdapter(): ConversationsAdapter {
        var currAdapter = binding.conversationsList.adapter
        if (currAdapter == null) {
            requireActivity().hideKeyboard()
            currAdapter = ConversationsAdapter(
                listener = this,
                recyclerView = binding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )
            binding.conversationsList.adapter = currAdapter
        }
        return currAdapter as ConversationsAdapter
    }

    private fun setupConversations(
        conversations: ArrayList<Conversation>,
        cached: Boolean = false,
    ) {
        val config = requireContext().config
        val sortedConversations = conversations
            .sortedWith(
                compareByDescending<Conversation> {
                    config.pinnedConversations.contains(it.threadId.toString())
                }.thenByDescending { it.date }
            ).toMutableList() as ArrayList<Conversation>

        if (cached && config.appRunCount == 1) {
            showOrHideProgress(conversations.isEmpty())
        } else {
            showOrHideProgress(false)
            showOrHidePlaceholder(conversations.isEmpty())
        }

        try {
            getOrCreateConversationsAdapter().apply {
                updateConversations(sortedConversations) {
                    if (!cached) {
                        showOrHidePlaceholder(currentList.isEmpty())
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun showOrHideProgress(show: Boolean) {
        if (show) {
            binding.conversationsProgressBar.show()
            binding.noConversationsPlaceholder.beVisible()
            binding.noConversationsPlaceholder.text = getString(R.string.loading_messages)
        } else {
            binding.conversationsProgressBar.hide()
            binding.noConversationsPlaceholder.beGone()
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        binding.conversationsFastscroller.beGoneIf(show)
        binding.noConversationsPlaceholder.beVisibleIf(show)
        binding.noConversationsPlaceholder.text = getString(R.string.no_conversations_found)
        binding.noConversationsPlaceholder2.beVisibleIf(show)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        getOrCreateConversationsAdapter().notifyDataSetChanged()
    }

    private fun handleConversationClick(any: Any) {
        Intent(requireContext(), ThreadActivity::class.java).apply {
            val conversation = any as Conversation
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            startActivity(this)
        }
    }

    private fun launchRecycleBin() {
        requireActivity().hideKeyboard()
        startActivity(Intent(requireContext(), RecycleBinConversationsActivity::class.java))
    }

    private fun launchArchivedConversations() {
        requireActivity().hideKeyboard()
        startActivity(Intent(requireContext(), ArchivedConversationsActivity::class.java))
    }

    private fun launchSettings() {
        requireActivity().hideKeyboard()
        startActivity(Intent(requireContext(), SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_EVENT_BUS or LICENSE_SMS_MMS or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(
                title = R.string.faq_2_title,
                text = R.string.faq_2_text
            ),
            FAQItem(
                title = R.string.faq_3_title,
                text = R.string.faq_3_text
            ),
            FAQItem(
                title = R.string.faq_4_title,
                text = R.string.faq_4_text
            ),
            FAQItem(
                title = org.fossify.commons.R.string.faq_9_title_commons,
                text = org.fossify.commons.R.string.faq_9_text_commons
            )
        )

        if (!resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)) {
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_2_title_commons,
                    text = org.fossify.commons.R.string.faq_2_text_commons
                )
            )
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_6_title_commons,
                    text = org.fossify.commons.R.string.faq_6_text_commons
                )
            )
        }

        (requireActivity() as? SimpleActivity)?.startAboutActivity(
            appNameId = R.string.app_name,
            licenseMask = licenses,
            versionName = BuildConfig.VERSION_NAME,
            faqItems = faqItems,
            showFAQBeforeMail = true
        )
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshConversations(@Suppress("unused") event: Events.RefreshConversations) {
        initMessenger()
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            (requireActivity() as? SimpleActivity)?.checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}