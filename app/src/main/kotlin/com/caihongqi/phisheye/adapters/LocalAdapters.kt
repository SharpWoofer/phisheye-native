package com.caihongqi.phisheye.adapters

import android.app.Activity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ActionMode
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import java.util.ArrayList
import java.util.HashSet

// Standalone ViewHolder to be shared
open class AdapterViewHolder(val view: View, val adapter: Any) : RecyclerView.ViewHolder(view) {
    
    val isSelectable: Boolean get() {
        return (adapter as? MyRecyclerViewAdapter)?.actMode != null || 
               (adapter as? MyRecyclerViewListAdapter<*>)?.actMode != null
    }

    fun bindView(any: Any, allowSingleClick: Boolean, allowLongClick: Boolean, callback: (itemView: View, adapterPosition: Int) -> Unit) {
        val currentAdapter = adapter as? MyRecyclerViewAdapter
        val currentListAdapter = adapter as? MyRecyclerViewListAdapter<*>
        
        view.setOnClickListener { 
            if (currentAdapter?.actMode != null) {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    currentAdapter.toggleItemSelection(!currentAdapter.selectedKeys.contains(currentAdapter.getItemSelectionKey(pos)), pos)
                }
            } else if (currentListAdapter?.actMode != null) {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    currentListAdapter.toggleItemSelection(!currentListAdapter.selectedKeys.contains(currentListAdapter.getItemSelectionKey(pos)), pos)
                }
            } else if (allowSingleClick) {
                if (currentAdapter != null) currentAdapter.itemClick(any)
                if (currentListAdapter != null) currentListAdapter.itemClick(any)
            }
        }
        view.setOnLongClickListener {
            if (allowLongClick) {
                if (currentAdapter?.actMode == null && currentListAdapter?.actMode == null) {
                   val pos = bindingAdapterPosition
                   if (pos != RecyclerView.NO_POSITION) {
                       currentAdapter?.toggleItemSelection(true, pos)
                       currentListAdapter?.toggleItemSelection(true, pos)
                   }
                }
                true
            } else {
                false
            }
        }
        callback(view, bindingAdapterPosition)
    }
    
    fun viewClicked(any: Any) {
         val currentAdapter = adapter as? MyRecyclerViewAdapter
         val currentListAdapter = adapter as? MyRecyclerViewListAdapter<*>
         if (currentAdapter?.actMode != null) {
            val pos = bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                currentAdapter.toggleItemSelection(!currentAdapter.selectedKeys.contains(currentAdapter.getItemSelectionKey(pos)), pos)
            }
        } else if (currentListAdapter?.actMode != null) {
            val pos = bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                currentListAdapter.toggleItemSelection(!currentListAdapter.selectedKeys.contains(currentListAdapter.getItemSelectionKey(pos)), pos)
            }
        } else {
            if (currentAdapter != null) currentAdapter.itemClick(any)
            if (currentListAdapter != null) currentListAdapter.itemClick(any)
        }
    }
    
    fun viewLongClicked() {
        val currentAdapter = adapter as? MyRecyclerViewAdapter
        val currentListAdapter = adapter as? MyRecyclerViewListAdapter<*>
        if (currentAdapter?.actMode == null && currentListAdapter?.actMode == null) {
           val pos = bindingAdapterPosition
           if (pos != RecyclerView.NO_POSITION) {
               currentAdapter?.toggleItemSelection(true, pos)
               currentListAdapter?.toggleItemSelection(true, pos)
           }
        }
    }
}

abstract class MyRecyclerViewAdapter(
    val activity: Activity,
    val recyclerView: RecyclerView,
    val itemClick: (Any) -> Unit
) : RecyclerView.Adapter<MyRecyclerViewAdapter.ViewHolder>() {

    val selectedKeys = HashSet<Int>()
    var actMode: ActionMode? = null
    
    val resources = activity.resources
    val layoutInflater: LayoutInflater = activity.layoutInflater
    val textColor: Int get() = activity.getProperTextColor()
    val properPrimaryColor: Int get() = activity.getProperPrimaryColor()

    abstract fun getActionMenuId(): Int
    abstract fun prepareActionMode(menu: Menu)
    abstract fun actionItemPressed(id: Int)
    abstract fun getSelectableItemCount(): Int
    abstract fun getIsItemSelectable(position: Int): Boolean
    abstract fun getItemSelectionKey(position: Int): Int?
    abstract fun getItemKeyPosition(key: Int): Int
    abstract fun onActionModeCreated()
    abstract fun onActionModeDestroyed()

    fun toggleItemSelection(select: Boolean, position: Int, updateSelection: Boolean = true) {
        val key = getItemSelectionKey(position) ?: return
        if (select) {
            selectedKeys.add(key)
        } else {
            selectedKeys.remove(key)
        }
        notifyItemChanged(position)
        
        if (selectedKeys.isEmpty()) {
            finishActMode()
        } else {
            if (actMode == null) {
                (activity as? AppCompatActivity)?.startSupportActionMode(actModeCallback)
            }
            updateTitle()
            // invalidate menu
            actMode?.invalidate()
        }
    }

    fun selectAll() {
        val count = itemCount
        for (i in 0 until count) {
            if (getIsItemSelectable(i)) {
                getItemSelectionKey(i)?.let { selectedKeys.add(it) }
            }
        }
        notifyDataSetChanged()
        updateTitle()
        actMode?.invalidate()
    }
    
    fun getSelectedItemPositions(): ArrayList<Int> {
        val positions = ArrayList<Int>()
        val count = itemCount
        for (i in 0 until count) {
            if (selectedKeys.contains(getItemSelectionKey(i))) {
                positions.add(i)
            }
        }
        return positions
    }
    
    fun removeSelectedItems(positions: List<Int>) {
        selectedKeys.clear()
        finishActMode()
        notifyDataSetChanged()
    }

    fun finishActMode() {
        actMode?.finish()
    }

    private fun updateTitle() {
        actMode?.title = "${selectedKeys.size}"
    }
    
    fun isOneItemSelected() = selectedKeys.size == 1
    
    fun setupDragListener(enabled: Boolean) {
    }
    
    fun createViewHolder(view: View): ViewHolder {
        return ViewHolder(view)
    }
    
    open inner class ViewHolder(view: View) : AdapterViewHolder(view, this@MyRecyclerViewAdapter)
    
    // Add missing bindViewHolder
    fun bindViewHolder(holder: ViewHolder) {
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION && actMode != null) {
            toggleItemSelection(selectedKeys.contains(getItemSelectionKey(position)), position, false)
            holder.itemView.isSelected = selectedKeys.contains(getItemSelectionKey(position))
        }
    }

    protected val actModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            actMode = mode
            if (getActionMenuId() != 0) {
                activity.menuInflater.inflate(getActionMenuId(), menu)
            }
            onActionModeCreated()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            prepareActionMode(menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            actionItemPressed(item.itemId)
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            selectedKeys.clear()
            actMode = null
            notifyDataSetChanged()
            onActionModeDestroyed()
        }
    }
}

abstract class MyRecyclerViewListAdapter<T>(
    val activity: Activity,
    val recyclerView: RecyclerView,
    diffCallback: DiffUtil.ItemCallback<T>,
    val itemClick: (Any) -> Unit,
    val onRefresh: (() -> Unit)? = null
) : ListAdapter<T, MyRecyclerViewListAdapter<T>.ViewHolder>(diffCallback) {

    val selectedKeys = HashSet<Int>()
    var actMode: ActionMode? = null
    
    val resources = activity.resources
    val layoutInflater: LayoutInflater = activity.layoutInflater
    val textColor: Int get() = activity.getProperTextColor()
    val properPrimaryColor: Int get() = activity.getProperPrimaryColor()

    abstract fun getActionMenuId(): Int
    abstract fun prepareActionMode(menu: Menu)
    abstract fun actionItemPressed(id: Int)
    abstract fun getSelectableItemCount(): Int
    abstract fun getIsItemSelectable(position: Int): Boolean
    abstract fun getItemSelectionKey(position: Int): Int?
    abstract fun getItemKeyPosition(key: Int): Int
    abstract fun onActionModeCreated()
    abstract fun onActionModeDestroyed()

    fun toggleItemSelection(select: Boolean, position: Int, updateSelection: Boolean = true) {
        val key = getItemSelectionKey(position) ?: return
        if (select) {
            selectedKeys.add(key)
        } else {
            selectedKeys.remove(key)
        }
        notifyItemChanged(position)
        
        if (selectedKeys.isEmpty()) {
            finishActMode()
        } else {
            if (actMode == null) {
                (activity as? AppCompatActivity)?.startSupportActionMode(actModeCallback)
            }
            updateTitle()
            actMode?.invalidate()
        }
    }

    fun selectAll() {
        val count = itemCount
        for (i in 0 until count) {
            if (getIsItemSelectable(i)) {
                getItemSelectionKey(i)?.let { selectedKeys.add(it) }
            }
        }
        notifyDataSetChanged()
        updateTitle()
        actMode?.invalidate()
    }
    
    fun getSelectedItemPositions(): ArrayList<Int> {
        val positions = ArrayList<Int>()
        val count = itemCount
        for (i in 0 until count) {
            if (selectedKeys.contains(getItemSelectionKey(i))) {
                positions.add(i)
            }
        }
        return positions
    }
    
    fun removeSelectedItems(positions: List<Int>) {
        selectedKeys.clear()
        finishActMode()
    }

    fun finishActMode() {
        actMode?.finish()
    }

    private fun updateTitle() {
        actMode?.title = "${selectedKeys.size}"
    }
    
    fun isOneItemSelected() = selectedKeys.size == 1
    
    fun setupDragListener(enabled: Boolean) {
    }
    
    fun createViewHolder(view: View): ViewHolder {
        return ViewHolder(view)
    }
    
    open inner class ViewHolder(view: View) : AdapterViewHolder(view, this@MyRecyclerViewListAdapter)
    
    // Add missing bindViewHolder
    fun bindViewHolder(holder: ViewHolder) {
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION && actMode != null) {
            holder.itemView.isSelected = selectedKeys.contains(getItemSelectionKey(position))
        }
    }

    protected val actModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            actMode = mode
            if (getActionMenuId() != 0) {
                activity.menuInflater.inflate(getActionMenuId(), menu)
            }
            onActionModeCreated()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            prepareActionMode(menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            actionItemPressed(item.itemId)
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            selectedKeys.clear()
            actMode = null
            notifyDataSetChanged()
            onActionModeDestroyed()
        }
    }
}
