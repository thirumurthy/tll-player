package com.thirutricks.tllplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
import android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
import android.widget.TextView
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.marginStart
import androidx.core.view.setPadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.thirutricks.tllplayer.models.TVListModel
import com.thirutricks.tllplayer.models.TVModel
import com.thirutricks.tllplayer.OrderPreferenceManager
import com.thirutricks.tllplayer.RenameDialogFragment
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
import android.widget.Toast
import android.view.MotionEvent
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class ListAdapter(
    private val context: Context,
    private val recyclerView: RecyclerView,
    var tvListModel: TVListModel,
) :
    RecyclerView.Adapter<ListAdapter.ViewHolder>() {
    private var listener: ItemListener? = null
    private var focused: View? = null
    private var defaultFocused = false
    private var defaultFocus: Int = -1

    var visible = false
    private lateinit var itemTouchHelper: ItemTouchHelper

    val application = context.applicationContext as MyTVApplication
    private var movingPosition = -1
    
    // Glass styling components
    private val styleConfig: GlassStyleConfig = GlassEffectUtils.getOptimalGlassConfig(context)
    private val performanceManager: GlassPerformanceManager = GlassPerformanceManager(context, styleConfig)
    private val animationController: MenuAnimationController = MenuAnimationController(styleConfig, performanceManager)
    private val feedbackManager: InteractiveFeedbackManager = InteractiveFeedbackManager(context, styleConfig, animationController)
    // private val scrollManager: GlassScrollManager = GlassScrollManager(context, styleConfig)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.list_item, parent, false)

        // Apply glass styling to the item with performance optimization
        performanceManager.applyPerformanceOptimizations(view)
        GlassEffectUtils.applyGlassStyle(view, styleConfig, GlassType.ITEM)

        val icon = view.findViewById<ImageView>(R.id.icon)
        val title = view.findViewById<TextView>(R.id.title)
        val heart = view.findViewById<ImageView>(R.id.heart)
        val description = view.findViewById<TextView>(R.id.description)

        // Apply responsive scaling for icon
        // icon.layoutParams.width = application.px2Px(icon.layoutParams.width)
        // icon.layoutParams.height = application.px2Px(icon.layoutParams.height)
        // icon.setPadding(application.px2Px(icon.paddingTop))

        // Apply responsive scaling for title
        // val layoutParams = title.layoutParams as ViewGroup.MarginLayoutParams
        // layoutParams.marginStart = application.px2Px(title.marginStart)
        // title.layoutParams = layoutParams

        // Apply responsive scaling for heart
        // heart.layoutParams.width = application.px2Px(heart.layoutParams.width)
        // heart.layoutParams.height = application.px2Px(heart.layoutParams.height)

        // Apply glass text styling
        GlassEffectUtils.applyGlassTextStyle(title, TextLevel.PRIMARY, styleConfig)
        // title.textSize = application.px2PxFont(title.textSize)

        val layoutParamsHeart = heart.layoutParams as ViewGroup.MarginLayoutParams
        // layoutParamsHeart.marginStart = application.px2Px(heart.marginStart)
        heart.layoutParams = layoutParamsHeart

        // Apply glass text styling for description
        GlassEffectUtils.applyGlassTextStyle(description, TextLevel.SECONDARY, styleConfig)
        // description.textSize = application.px2PxFont(description.textSize)

        // Set up glass state selector for interactive feedback
        view.background = GlassEffectUtils.createGlassStateSelector(context)
        view.isFocusable = true
        view.isFocusableInTouchMode = true

        // Set up interactive feedback for the item (not the heart)
        feedbackManager.setupInteractiveFeedback(view, FeedbackType.MENU_ITEM)

        return ViewHolder(context, view, styleConfig, animationController, feedbackManager)
    }

    fun focusable(able: Boolean) {
        recyclerView.isFocusable = able
        recyclerView.isFocusableInTouchMode = able
        if (able) {
            recyclerView.descendantFocusability = FOCUS_BEFORE_DESCENDANTS
        } else {
            recyclerView.descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        }
    }

    fun update(tvListModel: TVListModel) {
        recyclerView.post {
            this.tvListModel = tvListModel
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

                val categoryName = tvListModel.getName()
                val currentOrder = getCurrentChannelOrder()
                Collections.swap(currentOrder, from, to)
                OrderPreferenceManager.saveChannelOrder(categoryName, currentOrder)
                tvListModel.swap(from, to)
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

    fun clear() {
        focused?.clearFocus()
        recyclerView.invalidate()
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val tvModel = tvListModel.getTVModel(position)!!
        val view = viewHolder.itemView

        view.isFocusable = true
        view.isFocusableInTouchMode = true
//        view.alpha = 0.8F

        viewHolder.like(tvModel.like.value as Boolean)

        val heart = view.findViewById<ImageView>(R.id.heart)
        // Heart icon is now purely visual - no click functionality
        heart.isFocusable = false
        heart.isFocusableInTouchMode = false
        heart.isClickable = false

        if (!defaultFocused && position == defaultFocus) {
            view.requestFocus()
            defaultFocused = true
        }

        val onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            listener?.onItemFocusChange(tvModel, hasFocus)

            if (hasFocus) {
                viewHolder.focus(true)
                focused = view
                if (visible) {
                    if (position != tvListModel.position.value) {
                        tvListModel.setPosition(position)
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
                listener?.onItemClicked(tvModel)
            }
        }

        view.setOnLongClickListener {
            showChannelOptions(position, tvModel)
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
                            moveChannelUp(position)
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            moveChannelDown(position)
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            stopMove()
                            return@setOnKeyListener true
                        }
                    }
                }

               

                return@setOnKeyListener listener?.onKey(this, keyCode) ?: false
            }
            false
        }

        viewHolder.bindTitle(tvModel.tv.title)

        viewHolder.bindImage(tvModel.tv.logo, tvModel.tv.id)

        viewHolder.setArrows(movingPosition == position)
        viewHolder.setMoveMode(movingPosition == position)
        
        val arrowUp = view.findViewById<ImageView>(R.id.arrow_up)
        val arrowDown = view.findViewById<ImageView>(R.id.arrow_down)
        
        arrowUp.setOnClickListener {
            moveChannelUp(position)
        }
        arrowDown.setOnClickListener {
            moveChannelDown(position)
        }
    }

    override fun getItemCount() = tvListModel.size()

    class ViewHolder(
        private val context: Context, 
        itemView: View,
        private val styleConfig: GlassStyleConfig,
        private val animationController: MenuAnimationController,
        private val feedbackManager: InteractiveFeedbackManager
    ) : RecyclerView.ViewHolder(itemView), OnSharedPreferenceChangeListener {

        private var isMoving = false
        private val title: TextView = itemView.findViewById(R.id.title)
        private val icon: ImageView = itemView.findViewById(R.id.icon)
        private val heart: ImageView = itemView.findViewById(R.id.heart)
        private val description: TextView = itemView.findViewById(R.id.description)
        private val arrows: LinearLayout = itemView.findViewById(R.id.arrows)
        private val arrowUp: ImageView = itemView.findViewById(R.id.arrow_up)
        private val arrowDown: ImageView = itemView.findViewById(R.id.arrow_down)

        init {
            SP.setOnSharedPreferenceChangeListener(this)
        }

        fun bindTitle(text: String) {
            title.text = text
        }

        fun bindImage(url: String?, id: Int) {
            if (url.isNullOrBlank()) {
                val width = Utils.dpToPx(40)
                val height = Utils.dpToPx(40)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                val paint = Paint().apply {
                    color = styleConfig.textPrimaryColor
                    textSize = 32f
                    textAlign = Paint.Align.CENTER
                }
                val text = String.format("%3d", id + 1)
                val x = width / 2f
                val y = height / 2f - (paint.descent() + paint.ascent()) / 2
                canvas.drawText(text, x, y, paint)
                
                // Apply glass styling to generated channel number
                Glide.with(context)
                    .load(BitmapDrawable(context.resources, bitmap))
                    .centerInside()
                    .into(icon)
            } else {
                Glide.with(context)
                    .load(url)
                    .centerInside()
                    .into(icon)
            }
            
            // Apply glass frame to icon
            GlassEffectUtils.applyGlassStyle(icon, styleConfig, GlassType.ITEM)
        }

        fun focus(hasFocus: Boolean) {
            // Apply glass text styling based on focus
            val titleLevel = if (hasFocus) TextLevel.PRIMARY else TextLevel.SECONDARY
            val descriptionLevel = if (hasFocus) TextLevel.SECONDARY else TextLevel.TERTIARY
            
            GlassEffectUtils.applyGlassTextStyle(title, titleLevel, styleConfig)
            GlassEffectUtils.applyGlassTextStyle(description, descriptionLevel, styleConfig)
            
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

        fun like(liked: Boolean) {
            val heartIcon = if (liked) R.drawable.ic_heart else R.drawable.ic_heart_empty
            heart.setImageDrawable(ContextCompat.getDrawable(context, heartIcon))
            
            // Apply glass styling to heart - purely visual, no feedback messages
            if (liked) {
                // Animate heart with glass glow effect
                animationController.animateHeartLike(heart, true)
                heart.setColorFilter(styleConfig.favoriteActiveColor)
            } else {
                animationController.animateHeartLike(heart, false)
                heart.setColorFilter(styleConfig.favoriteInactiveColor)
            }
        }

        fun setArrows(isMoving: Boolean) {
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

        override fun onSharedPreferenceChanged(key: String) {
            Log.i(TAG, "$key changed")
            when (key) {
                SP.KEY_EPG -> {
                    if (SP.epg.isNullOrEmpty()) {
                        val constraintSet = ConstraintSet()
                        constraintSet.clone(itemView as androidx.constraintlayout.widget.ConstraintLayout)

                        constraintSet.connect(title.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                        constraintSet.connect(heart.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

                        constraintSet.applyTo(itemView as androidx.constraintlayout.widget.ConstraintLayout)

                        description.visibility = View.GONE
                    } else {
                        val constraintSet = ConstraintSet()
                        constraintSet.clone(itemView as androidx.constraintlayout.widget.ConstraintLayout)

                        constraintSet.clear(title.id, ConstraintSet.BOTTOM)
                        constraintSet.clear(heart.id, ConstraintSet.BOTTOM)

                        constraintSet.applyTo(itemView as androidx.constraintlayout.widget.ConstraintLayout)

                        description.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    fun toPosition(position: Int) {
        recyclerView.post {
            // Use glass scroll manager for smooth scrolling
            // scrollManager.smoothScrollToPosition(recyclerView, position)

            recyclerView.postDelayed({
                val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
                viewHolder?.itemView?.isSelected = true
                viewHolder?.itemView?.requestFocus()
            }, 100) // Slightly longer delay for smooth scroll
        }
    }

    interface ItemListener {
        fun onItemFocusChange(tvModel: TVModel, hasFocus: Boolean)
        fun onItemClicked(tvModel: TVModel)
        fun onKey(listAdapter: ListAdapter, keyCode: Int): Boolean
    }

    fun setItemListener(listener: ItemListener) {
        this.listener = listener
    }

    private fun showChannelOptions(position: Int, tvModel: TVModel) {
        val channelName = tvModel.tv.title

        val optionsDialog = ChannelOptionsDialogFragment.newInstance(channelName)
        optionsDialog.setChannelOptionsListener(object : ChannelOptionsDialogFragment.ChannelOptionsListener {
            override fun onMoveSelected() {
                startMove(position)
            }

            override fun onRenameSelected() {
                showRenameDialog(tvModel)
            }

            override fun onFavouriteSelected() {
                // Toggle favourite status for this channel
                val currentLiked = tvModel.like.value ?: false
                tvModel.setLike(!currentLiked)
                
                // Save to preferences
                SP.setLike(tvModel.tv.id, !currentLiked)
                
                // Update the UI immediately
                notifyItemChanged(position)
                
                // Refresh the Favourites category
                com.thirutricks.tllplayer.models.TVList.refreshFavourites()
                
                val message = if (!currentLiked) "Added to favourites" else "Removed from favourites"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }

            override fun onDeleteSelected() {
                // Delete the channel
                com.thirutricks.tllplayer.models.TVList.removeChannel(tvModel.tv)
                Toast.makeText(context, "Channel deleted", Toast.LENGTH_SHORT).show()
            }

            override fun onCancelSelected() {
                // Do nothing
            }
        })
        optionsDialog.show((context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager ?: return, "ChannelOptions")
    }

    private fun startMove(position: Int) {
        if (movingPosition != -1 && movingPosition != position) {
            notifyItemChanged(movingPosition)
        }
        movingPosition = position
        notifyItemChanged(position)
    }

    private fun stopMove() {
        val prevPosition = movingPosition
        movingPosition = -1
        notifyItemChanged(prevPosition)
    }



    private fun getCurrentChannelOrder(): MutableList<String> {
        val order = mutableListOf<String>()
        for (i in 0 until tvListModel.size()) {
            val model = tvListModel.getTVModel(i)
            if (model != null) {
                val url = model.tv.uris.firstOrNull() ?: model.tv.title
                order.add(url)
            }
        }
        return order
    }

    private fun showRenameDialog(tvModel: TVModel) {
        val channelUrl = tvModel.tv.uris.firstOrNull() ?: ""
        val currentName = tvModel.tv.title
        
        val renameDialog = RenameDialogFragment.newInstance(currentName, "Rename Channel")
        renameDialog.setRenameListener(object : RenameDialogFragment.RenameListener {
            override fun onRenameConfirmed(newName: String) {
                if (channelUrl.isNotEmpty()) {
                    OrderPreferenceManager.saveChannelRename(channelUrl, newName)
                    Toast.makeText(context, "Channel renamed", Toast.LENGTH_SHORT).show()
                    // Trigger refresh to apply rename
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        com.thirutricks.tllplayer.models.TVList.refreshModels()
                    }
                    // Update the adapter
                    update(tvListModel)
                }
            }
        })
        renameDialog.show((context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager ?: return, "RenameChannel")
    }

    private fun moveChannelUp(position: Int) {
        if (position <= 0) {
            Toast.makeText(context, "Cannot move this channel up", Toast.LENGTH_SHORT).show()
            return
        }

        val categoryName = tvListModel.getName()
        val currentOrder = getCurrentChannelOrder()
        val index = position
        
        if (index > 0 && index < currentOrder.size) {
            Collections.swap(currentOrder, index, index - 1)
            OrderPreferenceManager.saveChannelOrder(categoryName, currentOrder)

            // Swap in model and notify adapter move
            tvListModel.swap(index, index - 1)
            
            val newPosition = position - 1
            movingPosition = newPosition
            
            // Notify the move first
            notifyItemMoved(index, newPosition)
            
            // Reassign channel IDs after the move
            com.thirutricks.tllplayer.models.TVList.reassignChannelIds()
            
            // Update the moved item to show arrows
            recyclerView.post {
                notifyItemChanged(newPosition)
                
                // Focus the moved item after UI updates
                recyclerView.postDelayed({
                    val viewHolder = recyclerView.findViewHolderForAdapterPosition(newPosition)
                    viewHolder?.itemView?.let { view ->
                        view.requestFocus()
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    Toast.makeText(context, "Channel moved up", Toast.LENGTH_SHORT).show()
                }, 50)
            }
        }
    }

    private fun moveChannelDown(position: Int) {
        if (position >= tvListModel.size() - 1) {
            Toast.makeText(context, "Cannot move this channel down", Toast.LENGTH_SHORT).show()
            return
        }

        val categoryName = tvListModel.getName()
        val currentOrder = getCurrentChannelOrder()
        val index = position

        if (index < currentOrder.size - 1) {
            Collections.swap(currentOrder, index, index + 1)
            OrderPreferenceManager.saveChannelOrder(categoryName, currentOrder)

            // Swap in model and notify adapter move
            tvListModel.swap(index, index + 1)
            
            val newPosition = position + 1
            movingPosition = newPosition
            
            // Notify the move first
            notifyItemMoved(index, newPosition)
            
            // Reassign channel IDs after the move
            com.thirutricks.tllplayer.models.TVList.reassignChannelIds()
            
            // Update the moved item to show arrows
            recyclerView.post {
                notifyItemChanged(newPosition)
                
                // Focus the moved item after UI updates
                recyclerView.postDelayed({
                    val viewHolder = recyclerView.findViewHolderForAdapterPosition(newPosition)
                    viewHolder?.itemView?.let { view ->
                        view.requestFocus()
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    Toast.makeText(context, "Channel moved down", Toast.LENGTH_SHORT).show()
                }, 50)
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
        private const val TAG = "ListAdapter"
    }
}

