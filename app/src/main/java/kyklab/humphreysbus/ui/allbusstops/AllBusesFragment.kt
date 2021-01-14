package kyklab.humphreysbus.ui.allbusstops

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_all_buses.view.*
import kyklab.humphreysbus.R
import kyklab.humphreysbus.bus.Bus
import kyklab.humphreysbus.bus.BusUtils
import kyklab.humphreysbus.ui.BusDetailsActivity

class AllBusesFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_all_buses, container, false)
        view.rvAllBuses.apply {
            adapter = RvAdapter(BusUtils.buses)
            layoutManager = GridLayoutManager(context, 2)
        }
        return view
    }

    private inner class RvAdapter(private val items: List<Bus>) :
        RecyclerView.Adapter<RvAdapter.ViewHolder>() {

        private inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivBus: ImageView = itemView.findViewById(R.id.ivBus)
            val tvBus: TextView = itemView.findViewById(R.id.tvBus)

            init {
                itemView.setOnClickListener {
                    val intent = Intent(activity, BusDetailsActivity::class.java).apply {
                        putExtra("busname", items[adapterPosition].name)
                    }
                    activity?.startActivity(intent)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.fragment_all_buses_item,
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            // TODO: Generify
            val color = ResourcesCompat.getColor(
                resources, when (items[position].name) {
                    "Red" -> android.R.color.holo_red_dark
                    "Blue" -> android.R.color.holo_blue_dark
                    "Green" -> android.R.color.holo_green_dark
                    else -> android.R.color.black
                }, context?.theme
            )
            holder.ivBus.apply {
//                setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_bus, context.theme))
//                setColorFilter(ResourcesCompat.getColor(resources, color, context.theme))
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(color))
            }
            holder.tvBus.apply {
                text = items[position].name
                setTextColor(color)
            }
        }


        override fun getItemCount() = items.size
    }
}