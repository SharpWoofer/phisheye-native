package com.caihongqi.phisheye.adapters

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.fossify.messages.R
import org.fossify.messages.databinding.ItemShieldCardAppBinding
import org.fossify.messages.databinding.ItemShieldCardCallBinding
import org.fossify.messages.databinding.ItemShieldCardSmsBinding
import org.fossify.messages.databinding.ItemShieldEmptyBinding
import com.caihongqi.phisheye.models.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShieldAdapter(
    private val onAction: (Action, ShieldListItem) -> Unit
) : ListAdapter<ShieldListItem, RecyclerView.ViewHolder>(ShieldDiffCallback()) {

    enum class Action {
        DELETE, WHITE_LIST, MOVE_TO_INBOX
    }

    companion object {
        private const val TYPE_SMS = 1
        private const val TYPE_APP = 2
        private const val TYPE_CALL = 3
        private const val TYPE_EMPTY = 4
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ShieldListItem.SmsItem -> TYPE_SMS
            is ShieldListItem.AppMessageItem -> TYPE_APP
            is ShieldListItem.CallItem -> TYPE_CALL
            is ShieldListItem.EmptyState -> TYPE_EMPTY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SMS -> SmsViewHolder(
                ItemShieldCardSmsBinding.inflate(inflater, parent, false)
            )
            TYPE_APP -> AppViewHolder(
                ItemShieldCardAppBinding.inflate(inflater, parent, false)
            )
            TYPE_CALL -> CallViewHolder(
                ItemShieldCardCallBinding.inflate(inflater, parent, false)
            )
            TYPE_EMPTY -> EmptyViewHolder(
                ItemShieldEmptyBinding.inflate(inflater, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is SmsViewHolder -> holder.bind(item as ShieldListItem.SmsItem)
            is AppViewHolder -> holder.bind(item as ShieldListItem.AppMessageItem)
            is CallViewHolder -> holder.bind(item as ShieldListItem.CallItem)
            is EmptyViewHolder -> {}
        }
    }

    private fun formatTime(ts: Long): String {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    inner class SmsViewHolder(private val binding: ItemShieldCardSmsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        private var isExpanded = false

        fun bind(item: ShieldListItem.SmsItem) {
            binding.iconSource.setImageResource(R.drawable.ic_message_vector)
            binding.textSender.text = item.sender
            binding.textPreview.text = item.preview
            binding.textTime.text = formatTime(item.timestamp)
            binding.textMeta.text = "Confidence: ${(item.confidence * 100).toInt()}%"
            
            // Set initial state
            updateExpansionState()

            // Toggle expansion on click
            binding.root.setOnClickListener {
                isExpanded = !isExpanded
                updateExpansionState()
            }
            
            binding.btnPrimaryAction.text = "Move to Inbox"
            binding.btnPrimaryAction.setOnClickListener { onAction(Action.MOVE_TO_INBOX, item) }
            
            binding.btnSecondaryAction.text = "Delete"
            binding.btnSecondaryAction.setOnClickListener { onAction(Action.DELETE, item) }
        }

        private fun updateExpansionState() {
            if (isExpanded) {
                binding.textPreview.maxLines = Int.MAX_VALUE
                binding.textPreview.ellipsize = null
            } else {
                binding.textPreview.maxLines = 3
                binding.textPreview.ellipsize = TextUtils.TruncateAt.END
            }
        }
    }

    inner class AppViewHolder(private val binding: ItemShieldCardAppBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        private var isExpanded = false

        fun bind(item: ShieldListItem.AppMessageItem) {
            binding.iconSource.setImageResource(R.drawable.ic_message_vector) 
            binding.textSender.text = item.sender
            binding.textPreview.text = item.preview
            binding.textTime.text = formatTime(item.timestamp)
            binding.textMeta.text = "From ${item.appName}"
            
            updateExpansionState()

            binding.root.setOnClickListener {
                isExpanded = !isExpanded
                updateExpansionState()
            }
            
            binding.btnPrimaryAction.text = "Dismiss" 
            binding.btnPrimaryAction.setOnClickListener { onAction(Action.DELETE, item) }
            
            binding.btnSecondaryAction.text = "Report"
        }

        private fun updateExpansionState() {
            if (isExpanded) {
                binding.textPreview.maxLines = Int.MAX_VALUE
                binding.textPreview.ellipsize = null
            } else {
                binding.textPreview.maxLines = 3
                binding.textPreview.ellipsize = TextUtils.TruncateAt.END
            }
        }
    }

    inner class CallViewHolder(private val binding: ItemShieldCardCallBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ShieldListItem.CallItem) {
            binding.iconSource.setImageResource(R.drawable.ic_message_vector) // TODO: Phone icon
            binding.textSender.text = item.number
            binding.textPreview.text = item.reason ?: "Blocked Call"
            binding.textTime.text = formatTime(item.timestamp)
            binding.textMeta.text = if (item.duration != null) "Duration: ${item.duration}" else "Blocked"
            
            binding.btnPrimaryAction.text = "Delete"
            binding.btnPrimaryAction.setOnClickListener { onAction(Action.DELETE, item) }
            
            binding.btnSecondaryAction.text = "Whitelist"
        }
    }
    
    inner class EmptyViewHolder(binding: ItemShieldEmptyBinding) : RecyclerView.ViewHolder(binding.root)
}

class ShieldDiffCallback : DiffUtil.ItemCallback<ShieldListItem>() {
    override fun areItemsTheSame(oldItem: ShieldListItem, newItem: ShieldListItem): Boolean {
        if (oldItem is ShieldListItem.ContentItem && newItem is ShieldListItem.ContentItem) {
            return oldItem.id == newItem.id
        }
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: ShieldListItem, newItem: ShieldListItem): Boolean {
        return oldItem == newItem
    }
}
