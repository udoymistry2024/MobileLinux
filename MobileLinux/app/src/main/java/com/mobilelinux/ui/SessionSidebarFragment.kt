package com.mobilelinux.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.mobilelinux.R
import com.mobilelinux.terminal.TerminalSession
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.mobilelinux.service.LinuxService

/**
 * Left sidebar showing all open terminal sessions.
 * tmux-like session list with add/rename/close controls.
 */
class SessionSidebarFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SessionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_session_sidebar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rv_sessions)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = SessionAdapter(
            onSessionClick = { session ->
                viewModel.setActiveSession(session.id)
                (activity as? MainActivity)?.closeDrawer()
            },
            onSessionClose = { session ->
                val remaining = viewModel.closeSession(session.id, autoCreateFallback = false)
                if (remaining <= 0) {
                    (activity as? MainActivity)?.let { act ->
                        LinuxService.stop(act)
                        act.finishAffinity()
                    }
                }
            },
            onSessionRename = { session ->
                showRenameDialog(session)
            }
        )
        recyclerView.adapter = adapter

        val btnNewSession = view.findViewById<MaterialButton>(R.id.btn_new_session)
        val header = view.findViewById<View>(R.id.sidebar_header)
        val bottomContainer = view.findViewById<View>(R.id.sidebar_bottom_container)

        // Protect from camera notch and navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            header.updatePadding(top = insets.top + (8 * resources.displayMetrics.density).toInt())
            bottomContainer.updatePadding(bottom = insets.bottom + (12 * resources.displayMetrics.density).toInt())
            windowInsets
        }

        // New session button
        btnNewSession.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val session = viewModel.createSessionAsync()
                (activity as? MainActivity)?.apply {
                    showTerminalFragment(session)
                    closeDrawer()
                }
            }
        }

        // Settings button
        view.findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            (activity as? MainActivity)?.openSettings()
        }

        // Observe sessions
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.sessions.collectLatest { sessions ->
                adapter.submitList(sessions)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activeSessionId.collectLatest { activeId ->
                adapter.setActiveId(activeId)
            }
        }
    }

    private fun showRenameDialog(session: TerminalSession) {
        RenameSessionDialog.show(childFragmentManager, session.name) { newName ->
            viewModel.renameSession(session.id, newName)
        }
    }
}

/**
 * RecyclerView adapter for session list.
 */
class SessionAdapter(
    private val onSessionClick: (TerminalSession) -> Unit,
    private val onSessionClose: (TerminalSession) -> Unit,
    private val onSessionRename: (TerminalSession) -> Unit
) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    private var sessions = listOf<TerminalSession>()
    private var activeId: String? = null

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_session_name)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_session_status)
        val btnClose: ImageButton = itemView.findViewById(R.id.btn_session_close)
        val activeIndicator: View = itemView.findViewById(R.id.view_active_indicator)
    }

    fun submitList(list: List<TerminalSession>) {
        val oldList = sessions
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldList[oldItemPosition].id == list[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldList[oldItemPosition] == list[newItemPosition]
        })
        sessions = list
        diffResult.dispatchUpdatesTo(this)
    }

    fun setActiveId(id: String?) {
        if (activeId == id) return
        val oldPos = sessions.indexOfFirst { it.id == activeId }
        val newPos = sessions.indexOfFirst { it.id == id }
        activeId = id
        if (oldPos != -1) notifyItemChanged(oldPos)
        if (newPos != -1) notifyItemChanged(newPos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        val isActive = session.id == activeId

        holder.tvName.text = session.name
        holder.tvStatus.text = if (session.isAlive) "running" else "stopped"
        holder.activeIndicator.visibility = if (isActive) View.VISIBLE else View.INVISIBLE

        // Highlight active session
        holder.itemView.isSelected = isActive

        holder.itemView.setOnClickListener { onSessionClick(session) }
        holder.itemView.setOnLongClickListener { onSessionRename(session); true }
        holder.btnClose.setOnClickListener { onSessionClose(session) }
    }

    override fun getItemCount() = sessions.size
}
