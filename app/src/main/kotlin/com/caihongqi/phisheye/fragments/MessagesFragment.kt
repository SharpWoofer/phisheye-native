package com.caihongqi.phisheye.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.Release
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import com.caihongqi.phisheye.activities.*
import com.caihongqi.phisheye.adapters.ConversationsAdapter
import com.caihongqi.phisheye.adapters.SearchResultsAdapter
import org.fossify.messages.databinding.FragmentMessagesBinding
import com.caihongqi.phisheye.extensions.*
import com.caihongqi.phisheye.helpers.THREAD_ID
import com.caihongqi.phisheye.helpers.THREAD_TITLE
import com.caihongqi.phisheye.interfaces.ConversationInteractionListener
import com.caihongqi.phisheye.models.Conversation
import com.caihongqi.phisheye.models.Events
import com.caihongqi.phisheye.models.SearchResult
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

        // Apply system window insets to search bar only
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val layoutParams = binding.searchBarCard.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.topMargin = systemBars.top + 8.dpToPx() // 8dp base margin + status bar height
            binding.searchBarCard.layoutParams = layoutParams
            insets
        }

        binding.conversationsList.layoutManager = LinearLayoutManager(requireContext())
        binding.searchResultsList.layoutManager = LinearLayoutManager(requireContext())

        setupSearch()

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

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun setupSearch() {
        // Make the entire search bar clickable to focus SearchView
        binding.searchBarCard.setOnClickListener {
            binding.searchView.requestFocus()
            binding.searchView.isIconified = false
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchView.findFocus(), InputMethodManager.SHOW_IMPLICIT)
        }

        // Also make the search icon parent clickable if you add it back
        // binding.searchView.setOnClickListener {
        //     binding.searchView.requestFocus()
        // }

        // Setup SearchView
        binding.searchView.apply {
            queryHint = getString(R.string.search)
            maxWidth = Integer.MAX_VALUE

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchQuery = query ?: ""
                    searchMessages(searchQuery)
                    requireActivity().hideKeyboard()
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    searchQuery = newText ?: ""
                    searchMessages(searchQuery)

                    // Show/hide appropriate views
                    val isSearching = !newText.isNullOrEmpty()
                    binding.searchHolder.beVisibleIf(isSearching)
                    binding.mainHolder.beGoneIf(isSearching)
                    binding.conversationsFab.beGoneIf(isSearching)

                    return true
                }
            })
        }

        // Setup settings button
        binding.settingsButton.setOnClickListener {
            launchSettings()
        }

        // Setup more button with popup menu
        binding.moreButton.setOnClickListener { view ->
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.menuInflater.inflate(R.menu.menu_messages_more, popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
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

            popupMenu.show()
        }
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
            ) { handleSearchResultClick(it) }
            binding.searchResultsList.adapter = currAdapter
            searchResultsAdapter = currAdapter
        } else if (binding.searchResultsList.adapter == null) {
            // Reattach after tab switch / re-creation
            binding.searchResultsList.adapter = currAdapter
        }
        return currAdapter
    }

    private fun handleSearchResultClick(item: Any) {
        if (item is SearchResult) {
            Intent(requireContext(), ThreadActivity::class.java).apply {
                putExtra(THREAD_ID, item.threadId)
                putExtra(THREAD_TITLE, item.title)
                startActivity(this)
            }
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

        val properBackgroundColor = requireContext().getProperBackgroundColor()
        val properTextColor = requireContext().getProperTextColor()
        val properPrimaryColor = requireContext().getProperPrimaryColor()

        // Style the card with tinted background
        binding.searchBarCard.setCardBackgroundColor(properPrimaryColor.adjustAlpha(0.15f))

        // Color the icons
        binding.settingsButton.setColorFilter(properTextColor)
        binding.moreButton.setColorFilter(properTextColor)

        // Apply colors to search holder
        binding.searchHolder.setBackgroundColor(properBackgroundColor)

        binding.noConversationsPlaceholder2.setTextColor(properPrimaryColor)
        binding.noConversationsPlaceholder2.underlineText()
        binding.conversationsFastscroller.updateColors(properPrimaryColor)
        binding.conversationsProgressBar.setIndicatorColor(properPrimaryColor)
        binding.conversationsProgressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)

        // Ensure search adapter is always attached when we have a query
        if (searchQuery.isNotEmpty()) {
            binding.searchHolder.beVisible()
            binding.mainHolder.beGone()
            binding.conversationsFab.beGone()
            getOrCreateSearchResultsAdapter()   // re-attaches adapter if needed
            searchMessages(searchQuery)         // re-run current search to repopulate list
        } else {
            binding.searchHolder.beGone()
            binding.mainHolder.beVisible()
            binding.conversationsFab.beVisible()
        }

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
            FAQItem(R.string.faq_2_title, R.string.faq_2_text),
            FAQItem(R.string.faq_3_title, R.string.faq_3_text),
            FAQItem(R.string.faq_4_title, R.string.faq_4_text),
            FAQItem(org.fossify.commons.R.string.faq_9_title_commons, org.fossify.commons.R.string.faq_9_text_commons)
        )

        if (!resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(org.fossify.commons.R.string.faq_2_title_commons, org.fossify.commons.R.string.faq_2_text_commons))
            faqItems.add(FAQItem(org.fossify.commons.R.string.faq_6_title_commons, org.fossify.commons.R.string.faq_6_text_commons))
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
