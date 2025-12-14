package com.caihongqi.phisheye.fragments

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import com.caihongqi.phisheye.R
import com.caihongqi.phisheye.adapters.ShieldAdapter
import com.caihongqi.phisheye.databinding.FragmentShieldBinding
import com.caihongqi.phisheye.models.ShieldFilter
import com.caihongqi.phisheye.models.ShieldListItem
import com.caihongqi.phisheye.viewmodels.ShieldViewModel

class ShieldFragment : Fragment() {
    private var binding: FragmentShieldBinding? = null
    private val viewModel: ShieldViewModel by viewModels()
    private lateinit var adapter: ShieldAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentShieldBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupInsets()
        setupRecyclerView()
        setupTabs()
        setupObservers()
    }

    private fun setupInsets() {
        // Ensure the top card doesn't overlap the status bar
        binding?.root?.let { root ->
            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updatePadding(top = bars.top)
                insets
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ShieldAdapter { action, item ->
            when (action) {
                ShieldAdapter.Action.DELETE -> {
                    if (item is ShieldListItem.ContentItem) {
                        viewModel.deleteItem(item.id)
                    }
                }
                ShieldAdapter.Action.MOVE_TO_INBOX -> {
                    if (item is ShieldListItem.SmsItem) {
                        viewModel.moveToInbox(item)
                    }
                }
                else -> {
                    // Handle other actions if needed
                }
            }
        }

        binding?.shieldRecyclerView?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ShieldFragment.adapter
        }
    }

    private fun setupTabs() {
        binding?.apply {
            tabAll.setOnClickListener { viewModel.setFilter(ShieldFilter.ALL) }
            tabSms.setOnClickListener { viewModel.setFilter(ShieldFilter.SMS) }
            tabCalls.setOnClickListener { viewModel.setFilter(ShieldFilter.CALLS) }
            tabOther.setOnClickListener { viewModel.setFilter(ShieldFilter.OTHER) }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderStatus(state.isProtected)
                    renderTabs(state.filter)
                    adapter.submitList(state.items)
                }
            }
        }
    }

    private fun renderStatus(isProtected: Boolean) {
        val statusBinding = binding?.layoutStatus ?: return

        if (isProtected) {
            statusBinding.statusContainer.setBackgroundColor(Color.parseColor("#E8F5E9")) // Light Green
            statusBinding.statusTitle.text = "Protected"
            statusBinding.statusTitle.setTextColor(Color.parseColor("#2E7D32"))
            statusBinding.statusSubtitle.text = "PhishEye is actively monitoring for scams."
            statusBinding.statusIcon.setColorFilter(Color.parseColor("#2E7D32"))
            statusBinding.btnEnableProtection.visibility = View.GONE
        } else {
            statusBinding.statusContainer.setBackgroundColor(Color.parseColor("#FFF3E0")) // Light Orange
            statusBinding.statusTitle.text = "Limited protection"
            statusBinding.statusTitle.setTextColor(Color.parseColor("#E65100"))
            statusBinding.statusSubtitle.text = "Grant notification access to enable full protection."
            statusBinding.statusIcon.setColorFilter(Color.parseColor("#E65100"))
            statusBinding.btnEnableProtection.visibility = View.VISIBLE
            statusBinding.btnEnableProtection.setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
    }

    private fun renderTabs(currentFilter: ShieldFilter) {
        binding?.apply {
            updateTabStyle(tabAll, currentFilter == ShieldFilter.ALL)
            updateTabStyle(tabSms, currentFilter == ShieldFilter.SMS)
            updateTabStyle(tabCalls, currentFilter == ShieldFilter.CALLS)
            updateTabStyle(tabOther, currentFilter == ShieldFilter.OTHER)
        }
    }

    private fun updateTabStyle(view: TextView, isSelected: Boolean) {
        if (isSelected) {
            view.setBackgroundResource(R.drawable.bg_tab_selected)
            view.setTextColor(Color.WHITE)
        } else {
            view.setBackgroundResource(R.drawable.bg_tab_unselected)
            view.setTextColor(Color.GRAY)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
