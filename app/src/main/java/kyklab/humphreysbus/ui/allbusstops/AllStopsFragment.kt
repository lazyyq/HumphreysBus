package kyklab.humphreysbus.ui.allbusstops

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_all_stops.view.*
import kyklab.humphreysbus.Const
import kyklab.humphreysbus.R
import kyklab.humphreysbus.bus.BusUtils
import kyklab.humphreysbus.data.BusStop
import kyklab.humphreysbus.utils.lbm
import kyklab.humphreysbus.utils.sendBroadcast

class AllStopsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_all_stops, container, false)
        view.rvAllStops.apply {
            adapter = RvAdapter(BusUtils.stops)
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
            return view
        }
    }

    private inner class RvAdapter(private val items: List<BusStop>) :
        RecyclerView.Adapter<RvAdapter.ViewHolder>() {

        private inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvStopNo: TextView = itemView.findViewById(R.id.tvStopNo)
            val tvStopName: TextView = itemView.findViewById(R.id.tvStopName)

            init {
                itemView.setOnClickListener {
                    val stop = items[adapterPosition]
                    val intents = arrayOf(
                        Intent(Const.Intent.ACTION_SHOW_ON_MAP).apply {
                            putExtra(Const.Intent.EXTRA_STOP_ID, stop.id)
                            putExtra(Const.Intent.EXTRA_X_COR, stop.xCenter)
                            putExtra(Const.Intent.EXTRA_Y_COR, stop.yCenter.toFloat())
                        },
                        Intent(Const.Intent.ACTION_BACK_TO_MAP)
                    )
                    activity?.run {
                        lbm.sendBroadcast(*intents)
                        finish()
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.fragment_all_stops_item,
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            // TODO: Generify
            holder.tvStopNo.text = items[position].no
            holder.tvStopName.text = items[position].name
//                holder.tvBus.setTextColor(color)
        }


        override fun getItemCount() = items.size
    }
}