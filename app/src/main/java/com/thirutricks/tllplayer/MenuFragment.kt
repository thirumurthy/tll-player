package com.thirutricks.tllplayer

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.thirutricks.tllplayer.databinding.MenuBinding
import com.thirutricks.tllplayer.models.TVList
import com.thirutricks.tllplayer.models.TVListModel
import com.thirutricks.tllplayer.models.TVModel
import com.thirutricks.tllplayer.ui.glass.GlassMenuContainer
import com.thirutricks.tllplayer.ui.glass.PanelType
import com.thirutricks.tllplayer.ui.glass.GlassEffectUtils
import com.thirutricks.tllplayer.ui.glass.GlassAccessibilityManager
import com.thirutricks.tllplayer.ui.glass.GlassResponsiveManager
// import com.thirutricks.tllplayer.ui.glass.GlassScrollManager
import com.thirutricks.tllplayer.ui.glass.GlassPerformanceManager

class MenuFragment : Fragment(), GroupAdapter.ItemListener, ListAdapter.ItemListener {
    private var _binding: MenuBinding? = null
    private val binding get() = _binding!!

    private lateinit var groupAdapter: GroupAdapter
    private lateinit var listAdapter: ListAdapter
    private lateinit var glassMenuContainer: GlassMenuContainer
    private lateinit var glassAccessibilityManager: GlassAccessibilityManager
    private lateinit var glassResponsiveManager: GlassResponsiveManager
    // private lateinit var glassScrollManager: GlassScrollManager
    private lateinit var glassPerformanceManager: GlassPerformanceManager
    
    companion object {
        private const val TAG = "MenuFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        _binding = MenuBinding.inflate(inflater, container, false)

        // Initialize glass managers
        glassAccessibilityManager = GlassAccessibilityManager(context)
        glassResponsiveManager = GlassResponsiveManager(context)
        glassPerformanceManager = GlassPerformanceManager(context)
        // glassScrollManager = GlassScrollManager(context)
        
        // Initialize performance monitoring
        glassPerformanceManager.initialize(binding.root as ViewGroup)

        // Get reference to the glass menu container
        glassMenuContainer = binding.menu as GlassMenuContainer
        
        // Apply performance, accessibility, and responsive adjustments
        setupGlassMenuContainer()

        // Set up adapters with enhanced glass styling
        setupAdapters()

        // Set up click listener for hiding menu
        binding.menu.setOnClickListener {
            hideSelf()
        }

        return binding.root
    }
    
    private fun setupGlassMenuContainer() {
        val context = requireContext()
        
        // Check device capabilities
        val supportsAdvancedEffects = GlassEffectUtils.supportsAdvancedEffects(context)
        val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val isLowEndDevice = activityManager.isLowRamDevice
        
        // Apply responsive scaling first
        glassResponsiveManager.applyAdaptiveGlassLayout(binding.menu)
        
        // Apply accessibility enhancements
        glassAccessibilityManager.applyGlassAccessibilityEnhancements(binding.menu)
        
        // Apply fallback styling for limited graphics devices if needed
        if (glassAccessibilityManager.hasLimitedGraphicsCapabilities()) {
            glassAccessibilityManager.applyFallbackStyling(binding.menu)
        }
        
        // Apply performance adjustments to glass container
        glassMenuContainer.applyPerformanceAdjustments(
            hasHardwareAcceleration = supportsAdvancedEffects,
            isLowEndDevice = isLowEndDevice
        )
        
        // Apply accessibility adjustments to glass container
        val isHighContrastEnabled = glassAccessibilityManager.isHighContrastMode()
        val reduceMotionEnabled = false // This would need to be checked from system settings
        
        glassMenuContainer.applyAccessibilityAdjustments(
            isHighContrastEnabled = isHighContrastEnabled,
            reduceMotionEnabled = reduceMotionEnabled
        )
        
        // Log device characteristics for debugging
        Log.d(TAG, "Glass Menu Setup - TV Device: ${glassResponsiveManager.isTVDevice()}, " +
                "Resolution: ${glassResponsiveManager.getTVResolution()}, " +
                "High Contrast: $isHighContrastEnabled, " +
                "Advanced Effects: $supportsAdvancedEffects")
    }
    
    private fun setupAdapters() {
        val context = requireContext()
        
        // Set up group adapter
        groupAdapter = GroupAdapter(
            context,
            binding.group,
            TVList.groupModel,
        )
        binding.group.adapter = groupAdapter
        binding.group.layoutManager = LinearLayoutManager(context)
        groupAdapter.setItemListener(this)
        groupAdapter.attachItemTouchHelper()
        
        // Set up glass scrolling for categories
        // glassScrollManager.setupGlassScrolling(binding.group, "categories")

        // Set up list adapter
        var tvListModel = TVList.groupModel.getTVListModel(TVList.groupModel.position.value!!)
        if (tvListModel == null) {
            TVList.groupModel.setPosition(0)
        }
        tvListModel = TVList.groupModel.getTVListModel(TVList.groupModel.position.value!!)

        listAdapter = ListAdapter(
            requireContext(),
            binding.list,
            tvListModel!!,
        )
        binding.list.adapter = listAdapter
        binding.list.layoutManager = LinearLayoutManager(context)
        listAdapter.focusable(false)
        listAdapter.setItemListener(this)
        listAdapter.attachItemTouchHelper()
        
        // Set up glass scrolling for channels with category-specific memory
        val categoryId = tvListModel.getName()
        // glassScrollManager.setupGlassScrolling(binding.list, categoryId)
        
        // Initialize category label
        updateCategoryLabel(tvListModel.getName())
    }

    fun update() {
        if (!::groupAdapter.isInitialized) return
        
        // Use glass container's smooth content update
        glassMenuContainer.updatePanelContent(binding.group) {
            groupAdapter.update(TVList.groupModel)
        }

        var tvListModel = TVList.groupModel.getTVListModel(TVList.groupModel.position.value!!)
        if (tvListModel == null) {
            TVList.groupModel.setPosition(0)
        }
        tvListModel = TVList.groupModel.getTVListModel(TVList.groupModel.position.value!!)

        if (tvListModel != null) {
            glassMenuContainer.updatePanelContent(binding.list) {
                (binding.list.adapter as ListAdapter).update(tvListModel)
            }
        }
    }

    fun updateList(position: Int) {
        TVList.groupModel.setPosition(position)
        SP.positionGroup = position
        val tvListModel = TVList.groupModel.getTVListModel()
        Log.i(TAG, "updateList tvListModel $position ${tvListModel?.size()}")
        
        if (tvListModel != null) {
            glassMenuContainer.updatePanelContent(binding.list) {
                (binding.list.adapter as ListAdapter).update(tvListModel)
            }
            
            // Update category label with selected category name
            updateCategoryLabel(tvListModel.getName())
            
        // Update scroll manager with new category
        val categoryId = tvListModel.getName()
        // glassScrollManager.setupGlassScrolling(binding.list, categoryId)
        }
    }
    
    private fun updateCategoryLabel(categoryName: String) {
        val categoryLabel = binding.root.findViewById<android.widget.TextView>(R.id.category_label)
        categoryLabel?.let {
            val labelText = if (categoryName.isNotEmpty()) {
                getString(R.string.channels_in_category_with_name, categoryName)
            } else {
                getString(R.string.channels_in_category)
            }
            it.text = labelText
        }
    }

    private fun hideSelf() {
        requireActivity().supportFragmentManager.beginTransaction()
            .hide(this)
            .commit()
    }

    override fun onItemFocusChange(tvListModel: TVListModel, hasFocus: Boolean) {
        if (hasFocus) {
            // Animate panel focus with glass effects
            glassMenuContainer.animatePanelFocus(binding.group, hasFocus)
            
            // Transition to category panel
            glassMenuContainer.transitionToPanel(PanelType.CATEGORY)
            
            glassMenuContainer.updatePanelContent(binding.list) {
                (binding.list.adapter as ListAdapter).update(tvListModel)
            }
            (activity as MainActivity).menuActive()
        }
    }

    override fun onItemClicked(position: Int) {
        // Handle category item click
    }

    override fun onItemFocusChange(tvModel: TVModel, hasFocus: Boolean) {
        if (hasFocus) {
            // Animate panel focus with glass effects
            glassMenuContainer.animatePanelFocus(binding.list, hasFocus)
            
            // Transition to channel panel
            glassMenuContainer.transitionToPanel(PanelType.CHANNEL)
            
            (activity as MainActivity).menuActive()
        }
    }

    override fun onItemClicked(tvModel: TVModel) {
        TVList.setPosition(tvModel.tv.id)
        (activity as MainActivity).hideMenuFragment()
    }

    override fun onKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (listAdapter.itemCount == 0) {
                    Toast.makeText(context, "No channel yet", Toast.LENGTH_LONG).show()
                    return true
                }
                
                // Smooth transition to channel panel
                glassMenuContainer.transitionToPanel(PanelType.CHANNEL)
                
                groupAdapter.focusable(false)
                listAdapter.focusable(true)

                val tvModel = TVList.getTVModel()
                if (tvModel != null) {
                    if (tvModel.groupIndex == TVList.groupModel.position.value!!) {
                        Log.i(
                            TAG,
                            "list on show toPosition ${tvModel.tv.title} ${tvModel.listIndex}/${listAdapter.tvListModel.size()}"
                        )
                        listAdapter.toPosition(tvModel.listIndex)
                    } else {
                        listAdapter.toPosition(0)
                    }
                } else {
                    listAdapter.toPosition(0)
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                // Handle left navigation if needed
                return true
            }
        }
        return false
    }

    override fun onKey(listAdapter: ListAdapter, keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                // Smooth transition to category panel
                glassMenuContainer.transitionToPanel(PanelType.CATEGORY)
                
                groupAdapter.focusable(true)
                listAdapter.focusable(false)
                listAdapter.clear()
                Log.i(TAG, "group toPosition on left")
                groupAdapter.toPosition(TVList.groupModel.position.value!!)
                return true
            }
        }
        return false
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!::groupAdapter.isInitialized || !::listAdapter.isInitialized) {
            return
        }
        
        if (!hidden) {
            if (binding.list.isVisible) {
                val currentTvModel = TVList.getTVModel()
                if (currentTvModel != null) {
                    val groupIndex = currentTvModel.groupIndex
                    Log.i(
                        TAG,
                        "groupIndex $groupIndex ${TVList.groupModel.position.value!!}"
                    )

                    if (groupIndex == TVList.groupModel.position.value!!) {
                        if (listAdapter.tvListModel.getIndex() != currentTvModel.groupIndex) {
                            updateList(groupIndex)
                        }

                        Log.i(
                            TAG,
                            "list on show toPosition ${currentTvModel.tv.title} ${currentTvModel.listIndex}/${listAdapter.tvListModel.size()}"
                        )
                        listAdapter.toPosition(currentTvModel.listIndex)
                    } else {
                        listAdapter.toPosition(0)
                    }
                }
            }
            
            if (binding.group.isVisible) {
                Log.i(
                    TAG,
                    "group on show toPosition ${TVList.groupModel.position.value!!}/${TVList.groupModel.size()}"
                )
                groupAdapter.toPosition(TVList.groupModel.position.value!!)
            }
            (activity as MainActivity).menuActive()
        } else {
            view?.post {
                if (::groupAdapter.isInitialized) groupAdapter.visible = false
                if (::listAdapter.isInitialized) listAdapter.visible = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Position will be handled by glass container animations
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        // Clean up performance manager resources
        if (::glassPerformanceManager.isInitialized) {
            glassPerformanceManager.cleanup()
        }
        
        // Clean up scroll manager resources
        // if (::glassScrollManager.isInitialized) {
        //     glassScrollManager.cleanup()
        // }
        
        _binding = null
    }
}