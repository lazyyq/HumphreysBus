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
import kyklab.humphreysbus.Const
import kyklab.humphreysbus.databinding.ActivityAllBusStopViewBinding
import kyklab.humphreysbus.utils.lbm
import kyklab.humphreysbus.utils.reduceDragSensitivity

class AllBusAndStopActivity : FragmentActivity() {
    private lateinit var binding: ActivityAllBusStopViewBinding

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

        binding = ActivityAllBusStopViewBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.vp.adapter = ScreenSlidePagerAdapter(this)
        binding.vp.reduceDragSensitivity()
        TabLayoutMediator(binding.tab, binding.vp) { tab, position ->
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