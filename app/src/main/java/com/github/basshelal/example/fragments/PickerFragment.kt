package com.github.basshelal.example.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.basshelal.R
import kotlinx.android.synthetic.main.fragment_picker.*

class PickerFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        defaultExample_button.setOnClickListener {
            activity?.supportFragmentManager?.beginTransaction()
                    ?.replace(R.id.fragment_container, DefaultExampleFragment())
                    ?.addToBackStack("")
                    ?.commit()
        }

        trello_button.setOnClickListener {
            activity?.supportFragmentManager?.beginTransaction()
                    ?.replace(R.id.fragment_container, TrelloFragment())
                    ?.addToBackStack("")
                    ?.commit()
        }
    }


}