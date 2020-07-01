package com.github.basshelal.example.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.github.basshelal.R
import com.github.basshelal.boardview.BoardAdapter
import com.github.basshelal.boardview.BoardColumnViewHolder
import com.github.basshelal.boardview.BoardContainerAdapter
import com.github.basshelal.boardview.BoardItemViewHolder
import com.github.basshelal.boardview.BoardListAdapter
import com.github.basshelal.example.Board
import com.github.basshelal.example.EXAMPLE_BOARD
import kotlinx.android.synthetic.main.fragment_trello.*
import kotlinx.android.synthetic.main.view_itemview_trello.view.*

class TrelloFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_trello, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        trelloBoardViewContainer.adapter = TrelloBoardContainerAdapter(EXAMPLE_BOARD)
    }
}

private class TrelloBoardContainerAdapter(val board: Board<String>) : BoardContainerAdapter() {

    override val headerLayoutRes: Int? = R.layout.view_header_trello

    override val footerLayoutRes: Int? = R.layout.view_footer_trello

    override val isListWrapContent: Boolean = true

    override val boardViewAdapter: BoardAdapter
        get() = TrelloBoardAdapter()

    override fun onCreateListAdapter(position: Int): BoardListAdapter<*> {
        return TrelloListAdapter(position)
    }

    private inner class TrelloBoardAdapter : BoardAdapter(this) {

        override fun getItemId(position: Int): Long = board.boardLists[position].id

        override fun getItemCount(): Int = board.boardLists.size
    }

    private inner class TrelloListAdapter(position: Int) : BoardListAdapter<TrelloItemViewHolder>() {

        var list = board[position]

        override fun getItemId(position: Int): Long = list[position].id

        override fun getItemCount(): Int = list.items.size

        override fun bindAdapter(holder: BoardColumnViewHolder, position: Int) {
            list = board[position]
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrelloItemViewHolder {
            return TrelloItemViewHolder(parent)
        }

        override fun onBindViewHolder(holder: TrelloItemViewHolder, position: Int) {
            holder.textView?.text = list[position].value
        }

    }
}

private class TrelloItemViewHolder(itemView: View) : BoardItemViewHolder(itemView) {
    val cardView: CardView? get() = itemView as? CardView
    val textView: TextView? get() = itemView.trello_item_textView

    constructor(parent: ViewGroup) : this(
            LayoutInflater.from(parent.context).inflate(R.layout.view_itemview_trello, parent, false)
    )
}