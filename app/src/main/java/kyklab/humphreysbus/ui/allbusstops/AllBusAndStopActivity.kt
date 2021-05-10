package kyklab.humphreysbus.ui.allbusstops

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.activity_all_bus_stop_view.*
import kyklab.humphreysbus.Const
import kyklab.humphreysbus.R
import kyklab.humphreysbus.utils.lbm
import kyklab.humphreysbus.utils.reduceDragSensitivity

class AllBusAndStopActivity : FragmentActivity() {
    private val intentFilter = IntentFilter(Const.Intent.ACTION_BACK_TO_MAP)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Const.Intent.ACTION_BACK_TO_MAP) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_bus_stop_view)

        vp.adapter = ScreenSlidePagerAdapter(this)
        vp.reduceDragSensitivity()
        TabLayoutMediator(tab, vp) { tab, position ->
            tab.text = when (position) {
                0 -> "Bus"
                1 -> "Stop"
                else -> "wtf?"
            }
        }.attach()

        lbm.registerReceiver(receiver, intentFilter)
    }

    override fun onDestroy() {
        lbm.unregisterReceiver(receiver)
        super.onDestroy()
    }

    private inner class ScreenSlidePagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment =
            when (position) {
                0 -> AllBusesFragment()
                else -> AllStopsFragment()
            }
    }
}