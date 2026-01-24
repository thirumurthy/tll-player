package com.thirutricks.tllplayer

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginStart
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thirutricks.tllplayer.models.TVGroupModel
import com.thirutricks.tllplayer.models.TVListModel
import com.thirutricks.tllplayer.OrderPreferenceManager
import com.thirutricks.tllplayer.RenameDialogFragment
import com.thirutricks.tllplayer.SP
import android.widget.Toast
import com.thirutricks.tllplayer.ui.glass.GlassEffectUtils
import com.thirutricks.tllplayer.ui.glass.GlassStyleConfig
import com.thirutricks.tllplayer.ui.glass.GlassType
import com.thirutricks.tllplayer.ui.glass.MenuAnimationController
import com.thirutricks.tllplayer.ui.glass.TextLevel
import com.thirutricks.tllplayer.ui.glass.InteractiveFeedbackManager
import com.thirutricks.tllplayer.ui.glass.FeedbackType
import com.thirutricks.tllplayer.ui.glass.OperationType
import com.thirutricks.tllplayer.ui.glass.GlassPerformanceManager
// import com.thirutricks.tllplayer.ui.glass.GlassScrollManager
import android.view.MotionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Collections


class GroupAdapter(
    private val context: Context,
    private val recyclerView: RecyclerView,
    private var tvGroupModel: TVGroupModel,
) :
    RecyclerView.Adapter<GroupAdapter.ViewHolder>() {

    private var listener: ItemListener? = null
    private var focused: View? = null
    private var defaultFocused = false
    private var defaultFocus: Int = -1
    private lateinit var itemTouchHelper: ItemTouchHelper

    val application = context.applicationContext as MyTVApplication
    var visible = false
    private var movingPosition = -1
    
    // Glass styling components
    private val styleConfig: GlassStyleConfig = GlassEffectUtils.getOptimalGlassConfig(context)
    private val performanceManager: GlassPerformanceManager = GlassPerformanceManager(context, styleConfig)
    private val animationController: MenuAnimationController = MenuAnimationController(styleConfig, performanceManager)
    private val feedbackManager: InteractiveFeedbackManager = InteractiveFeedbackManager(context, styleConfig, animationController)
    // private val scrollManager: GlassScrollManager = GlassScrollManager(context, styleConfig)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.group_item, parent, false)

        // Apply glass styling to the item with performance optimization
        performanceManager.applyPerformanceOptimizations(view)
        GlassEffectUtils.applyGlassStyle(view, styleConfig, GlassType.ITEM)

        val title = view.findViewById<TextView>(R.id.title)
        
        // Apply responsive scaling
        val layoutParams = title.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.marginStart = application.px2Px(title.marginStart)
        layoutParams.bottomMargin = application.px2Px(title.marginBottom)
        title.layoutParams = layoutParams

        // Apply glass text styling
        GlassEffectUtils.applyGlassTextStyle(title, TextLevel.PRIMARY, styleConfig)
        title.textSize = application.px2PxFont(title.textSize)

        // Set up glass state selector for interactive feedback
        view.background = GlassEffectUtils.createGlassStateSelector(context)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        
        // Set up interactive feedback
        feedbackManager.setupInteractiveFeedback(view, FeedbackType.MENU_ITEM)
        
        return ViewHolder(context, view, styleConfig, animationController, feedbackManager)
    }

    fun focusable(able: Boolean) {
        recyclerView.isFocusable = able
        recyclerView.isFocusableInTouchMode = able
        if (able) {
            recyclerView.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        } else {
            recyclerView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    }

    fun clear() {
        focused?.clearFocus()
        recyclerView.invalidate()
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val tvListModel = tvGroupModel.getTVListModel(position)!!
        val view = viewHolder.itemView
        view.tag = position

        if (!defaultFocused && position == defaultFocus) {
            view.requestFocus()
            defaultFocused = true
        }

        val onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            listener?.onItemFocusChange(tvListModel, hasFocus)

            if (hasFocus) {
                viewHolder.focus(true)
                focused = view
                if (visible) {
                    if (position != tvGroupModel.position.value) {
                        tvGroupModel.setPosition(position)
                    }
                } else {
                    visible = true
                }
            } else {
                viewHolder.focus(false)
            }
        }

        view.onFocusChangeListener = onFocusChangeListener

        view.setOnClickListener { _ ->
            if (movingPosition == position) {
                stopMove()
            } else {
                listener?.onItemClicked(position)
            }
        }

        view.setOnLongClickListener {
            showCategoryOptions(position, tvListModel)
            true
        }

        view.setOnKeyListener { _, keyCode, event: KeyEvent? ->
            if (event?.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && position == 0) {
                    val p = getItemCount() - 1

                    (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                        p,
                        0
                    )

                    recyclerView.postDelayed({
                        val v = recyclerView.findViewHolderForAdapterPosition(p)
                        v?.itemView?.isSelected = true
                        v?.itemView?.requestFocus()
                    }, 0)
                }

                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && position == getItemCount() - 1) {
                    val p = 0

                    (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                        p,
                        0
                    )

                    recyclerView.postDelayed({
                        val v = recyclerView.findViewHolderForAdapterPosition(p)
                        v?.itemView?.isSelected = true
                        v?.itemView?.requestFocus()
                    }, 0)
                }

                if (movingPosition != -1 && movingPosition == position) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            moveGroupUp(position)
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            moveGroupDown(position)
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            stopMove()
                            return@setOnKeyListener true
                        }
                    }
                }

                return@setOnKeyListener listener?.onKey(keyCode) ?: false
            }
            false
        }

        viewHolder.bindTitle(tvListModel.getName())
        viewHolder.setArrowsVisibility(movingPosition == position)
        viewHolder.setMoveMode(movingPosition == position)
        
        val arrowUp = view.findViewById<ImageView>(R.id.arrow_up)
        val arrowDown = view.findViewById<ImageView>(R.id.arrow_down)
        
        arrowUp.setOnClickListener {
            moveGroupUp(position)
        }
        arrowDown.setOnClickListener {
            moveGroupDown(position)
        }
    }

    override fun getItemCount() = tvGroupModel.size()

    class ViewHolder(
        private val context: Context, 
        itemView: View,
        private val styleConfig: GlassStyleConfig,
        private val animationController: MenuAnimationController,
        private val feedbackManager: InteractiveFeedbackManager
    ) : RecyclerView.ViewHolder(itemView) {
        
        private var isMoving = false
        private val title: TextView = itemView.findViewById(R.id.title)
        private val arrows: LinearLayout = itemView.findViewById(R.id.arrows)
        private val arrowUp: ImageView = itemView.findViewById(R.id.arrow_up)
        private val arrowDown: ImageView = itemView.findViewById(R.id.arrow_down)
        
        fun bindTitle(text: String) {
            title.text = text
        }

        fun setArrowsVisibility(isMoving: Boolean) {
            arrows.visibility = if (isMoving) View.VISIBLE else View.GONE
            
            // Apply glass styling to arrows
            if (isMoving) {
                GlassEffectUtils.applyGlassStyle(arrowUp, styleConfig, GlassType.ITEM)
                GlassEffectUtils.applyGlassStyle(arrowDown, styleConfig, GlassType.ITEM)
                
                // Add glass glow effect to arrows
                arrowUp.setColorFilter(styleConfig.moveGlowColor)
                arrowDown.setColorFilter(styleConfig.moveGlowColor)
            }
        }
        
        fun setMoveMode(isMoving: Boolean) {
            if (this.isMoving != isMoving) {
                this.isMoving = isMoving
                
                // Apply move mode glass styling
                if (isMoving) {
                    GlassEffectUtils.applyGlassStyle(itemView, styleConfig, GlassType.ITEM_MOVING)
                    animationController.animateMoveMode(itemView, true)
                } else {
                    GlassEffectUtils.applyGlassStyle(itemView, styleConfig, GlassType.ITEM)
                    animationController.animateMoveMode(itemView, false)
                }
            }
        }

        fun focus(hasFocus: Boolean) {
            // Apply glass text styling based on focus
            val textLevel = if (hasFocus) TextLevel.PRIMARY else TextLevel.SECONDARY
            GlassEffectUtils.applyGlassTextStyle(title, textLevel, styleConfig)
            
            // Apply bold styling only when focused (selected)
            if (hasFocus) {
                title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
            } else {
                title.setTypeface(title.typeface, android.graphics.Typeface.NORMAL)
            }

            // Apply glass focus styling
            if (hasFocus && !isMoving) {
                GlassEffectUtils.applyGlassStyle(itemView, styleConfig, GlassType.ITEM_FOCUSED)
            } else if (!isMoving) {
                GlassEffectUtils.applyGlassStyle(itemView, styleConfig, GlassType.ITEM)
            }

            // Use glass animation controller for smooth focus transitions
            animationController.animateFocus(itemView, hasFocus)
        }
    }

    fun toPosition(position: Int) {
        recyclerView.post {
            // Immediate focus change without delay for better responsiveness
            val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
            viewHolder?.itemView?.let { itemView ->
                itemView.isSelected = true
                itemView.requestFocus()
            }
        }
    }

    interface ItemListener {
        fun onItemFocusChange(tvListModel: TVListModel, hasFocus: Boolean)
        fun onItemClicked(position: Int)
        fun onKey(keyCode: Int): Boolean
    }

    fun setItemListener(listener: ItemListener) {
        this.listener = listener
    }

    fun update(tvGroupModel: TVGroupModel) {
        this.tvGroupModel = tvGroupModel
        recyclerView.post {
            notifyDataSetChanged()
        }
    }

    fun attachItemTouchHelper() {
        val callback = object : ItemTouchHelper.Callback() {
            override fun isLongPressDragEnabled() = false
            override fun isItemViewSwipeEnabled() = false

            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, 0)
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition

                if (from <= 1 || to <= 1) return false // can't move first two

                val currentOrder = getCurrentCategoryOrder()
                Collections.swap(currentOrder, from - 2, to - 2)
                OrderPreferenceManager.saveCategoryOrder(currentOrder)
                tvGroupModel.swap(from, to)
                notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // no swipe
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun showCategoryOptions(position: Int, tvListModel: TVListModel) {
        val displayName = tvListModel.getName()
        val renames = OrderPreferenceManager.getCategoryRenames()
        val originalName = renames.entries.find { it.value == displayName }?.key ?: displayName

        val optionsDialog = CategoryOptionsDialogFragment.newInstance(displayName)
        optionsDialog.setCategoryOptionsListener(object : CategoryOptionsDialogFragment.CategoryOptionsListener {
            override fun onMoveSelected() {
                startMove(position)
            }

            override fun onRenameSelected() {
                showRenameDialog(originalName)
            }

            override fun onCancelSelected() {
                // Do nothing
            }
        })
        optionsDialog.show((context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager ?: return, "CategoryOptions")
    }

    private fun startMove(position: Int) {
        if (position <= 1) {
            Toast.makeText(context, "Cannot move this category", Toast.LENGTH_SHORT).show()
            return
        }
        if (movingPosition != -1 && movingPosition != position) {
            notifyItemChanged(movingPosition)
        }
        movingPosition = position
        notifyItemChanged(position)
    }

    private fun stopMove() {
        val prevPosition = movingPosition
        movingPosition = -1
        if (prevPosition >= 0) {
            notifyItemChanged(prevPosition)
        }
    }
    
    /**
     * Reset move state and refresh adapter after category moves
     */
    private fun resetMoveStateAndRefresh() {
        movingPosition = -1
        // Full refresh to ensure all positions are synchronized
        notifyDataSetChanged()
    }

    private fun getCurrentCategoryOrder(): MutableList<String> {
        val order = mutableListOf<String>()
        for (i in 3 until tvGroupModel.size()) { // Start from index 3, skip first 3 categories
            val model = tvGroupModel.getTVListModel(i)
            if (model != null) {
                // Get original name (before rename)
                val displayName = model.getName()
                val renames = OrderPreferenceManager.getCategoryRenames()
                val originalName = renames.entries.find { it.value == displayName }?.key ?: displayName
                order.add(originalName)
            }
        }
        Log.d(TAG, "getCurrentCategoryOrder: ${order.joinToString(", ")}")
        return order
    }

    private fun showRenameDialog(originalName: String) {
        val renameDialog = RenameDialogFragment.newInstance(originalName, "Rename Category")
        renameDialog.setRenameListener(object : RenameDialogFragment.RenameListener {
            override fun onRenameConfirmed(newName: String) {
                OrderPreferenceManager.saveCategoryRename(originalName, newName)
                
                // Show operation completion feedback
                val currentView = focused
                currentView?.let { view ->
                    feedbackManager.showOperationCompletionFeedback(
                        view, 
                        OperationType.RENAME, 
                        true, 
                        "Category renamed to $newName"
                    )
                }
                
                Toast.makeText(context, "Category renamed", Toast.LENGTH_SHORT).show()
                // Trigger refresh to apply rename
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    com.thirutricks.tllplayer.models.TVList.refreshModels()
                }
                // Update the adapter
                update(tvGroupModel)
            }
        })
        renameDialog.show((context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager ?: return, "RenameCategory")
    }

    private fun moveGroupUp(position: Int) {
        Log.d(TAG, "moveGroupUp: position=$position, movingPosition=$movingPosition")
        
        if (position <= 3) {
            Toast.makeText(context, "Cannot move this category up", Toast.LENGTH_SHORT).show()
            return
        }

        val currentOrder = getCurrentCategoryOrder()
        val index = position - 3
        
        Log.d(TAG, "moveGroupUp: index=$index, currentOrder size=${currentOrder.size}")

        if (index > 0 && index < currentOrder.size) {
            Collections.swap(currentOrder, index, index - 1)
            OrderPreferenceManager.saveCategoryOrder(currentOrder)
            tvGroupModel.swap(position, position - 1)
            
            val newPosition = position - 1
            movingPosition = newPosition
            
            Log.d(TAG, "moveGroupUp: swapped $position -> $newPosition")
            
            // Notify the move first
            notifyItemMoved(position, newPosition)
            
            // Rebuild the entire channel list to reflect new category order
            com.thirutricks.tllplayer.models.TVList.rebuildChannelListFromCategories()
            
            // Update the moved item to show arrows and refresh adapter state
            recyclerView.post {
                // Refresh the entire adapter to sync positions
                notifyDataSetChanged()
                
                // Focus the moved item after UI updates
                recyclerView.postDelayed({
                    val viewHolder = recyclerView.findViewHolderForAdapterPosition(newPosition)
                    viewHolder?.itemView?.let { view ->
                        view.requestFocus()
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    Toast.makeText(context, "Category moved up", Toast.LENGTH_SHORT).show()
                }, 100)
            }
        } else {
            Log.w(TAG, "moveGroupUp: Invalid index $index for order size ${currentOrder.size}")
        }
    }

    private fun moveGroupDown(position: Int) {
        if (position >= tvGroupModel.size() - 1) {
            Toast.makeText(context, "Cannot move this category down", Toast.LENGTH_SHORT).show()
            return
        }

        val currentOrder = getCurrentCategoryOrder()
        val index = position - 3

        if (index < currentOrder.size - 1) {
            Collections.swap(currentOrder, index, index + 1)
            OrderPreferenceManager.saveCategoryOrder(currentOrder)
            tvGroupModel.swap(position, position + 1)
            
            val newPosition = position + 1
            movingPosition = newPosition
            
            // Notify the move first
            notifyItemMoved(position, newPosition)
            
            // Rebuild the entire channel list to reflect new category order
            com.thirutricks.tllplayer.models.TVList.rebuildChannelListFromCategories()
            
            // Update the moved item to show arrows and refresh adapter state
            recyclerView.post {
                // Refresh the entire adapter to sync positions
                notifyDataSetChanged()
                
                // Focus the moved item after UI updates
                recyclerView.postDelayed({
                    val viewHolder = recyclerView.findViewHolderForAdapterPosition(newPosition)
                    viewHolder?.itemView?.let { view ->
                        view.requestFocus()
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    Toast.makeText(context, "Category moved down", Toast.LENGTH_SHORT).show()
                }, 100)
            }
        }
    }
    
    /**
     * Clean up animations and feedback when adapter is detached
     */
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        animationController.cancelAllAnimations()
        feedbackManager.cleanup()
        // scrollManager.cleanup()
        performanceManager.cleanup()
    }

    companion object {
        private const val TAG = "CategoryAdapter"
    }
}

