package kyklab.humphreysbus.ui.allbusstops

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kyklab.humphreysbus.R
import kyklab.humphreysbus.bus.Bus
import kyklab.humphreysbus.bus.BusUtils
import kyklab.humphreysbus.databinding.FragmentAllBusesBinding
import kyklab.humphreysbus.ui.BusTrackActivity

class AllBusesFragment : Fragment() {
    private lateinit var binding: FragmentAllBusesBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAllBusesBinding.inflate(inflater)
        val view = binding.root
        binding.rvAllBuses.apply {
            adapter = RvAdapter(BusUtils.buses)
            layoutManager = LinearLayoutManager(context)
            /*
            layoutManager = GridLayoutManager(context, 2).apply {
                orientation = GridLayoutManager.VERTICAL
                spanSizeLookup = object: GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        val adapter = adapter as? RvAdapter ?: return -1
                        return if (position == adapter.itemCount - 1) 2 else 1
                    }
                }
            }
            */
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
                    val intent = Intent(activity, BusTrackActivity::class.java).apply {
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
            val color = items[position].colorInt
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